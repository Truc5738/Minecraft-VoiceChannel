package dev.truc5738.voicechannel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceManager {
    private final JavaPlugin plugin;
    private final Map<UUID, String> channels = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> mutedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerVolumes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> micMuted = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> outputMuted = new ConcurrentHashMap<>();
    private final Map<UUID, Double> ranges = new ConcurrentHashMap<>();
    private final Map<UUID, VoiceRoute> routes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> speakingUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> connected = new ConcurrentHashMap<>();
    private final Map<String, UUID> privateOwners = new ConcurrentHashMap<>();
    private final Map<String, String> privatePasswords = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> channelMembers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> channelMuted = new ConcurrentHashMap<>();

    public VoiceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguredChannels();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshRoutes, 1L, 2L);
    }

    private void loadConfiguredChannels() {
        String configured = plugin.getConfig().getString("voice.default-channel", "General");
        channelMembers.putIfAbsent(configured, ConcurrentHashMap.newKeySet());
        if (plugin.getConfig().getConfigurationSection("channels") != null) {
            for (String name : plugin.getConfig().getConfigurationSection("channels").getKeys(false)) {
                channelMembers.putIfAbsent(name, ConcurrentHashMap.newKeySet());
            }
        }
    }

    public Set<String> getChannels() {
        return Set.copyOf(channelMembers.keySet());
    }

    public boolean joinChannel(Player player, String channel) {
        if (channel == null || channel.isBlank()) return false;
        String target = channel.trim();
        if (!channelMembers.containsKey(target) && !privateOwners.containsKey(target)) return false;
        String current = getChannel(player);
        channelMembers.computeIfAbsent(current, ignored -> ConcurrentHashMap.newKeySet()).remove(player.getUniqueId());
        channelMembers.computeIfAbsent(target, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        channels.put(player.getUniqueId(), target);
        refreshRoute(player);
        return true;
    }

    public boolean createPrivateChannel(Player owner, String name, String password) {
        if (name == null || name.isBlank() || password == null) return false;
        String target = name.trim();
        if (target.length() > 24 || target.contains(" ") || channelMembers.containsKey(target) || privateOwners.containsKey(target)) return false;
        privateOwners.put(target, owner.getUniqueId());
        privatePasswords.put(target, password);
        channelMembers.put(target, ConcurrentHashMap.newKeySet());
        return joinChannel(owner, target);
    }

    public boolean joinPrivateChannel(Player player, String name, String password) {
        return privateOwners.containsKey(name) && java.util.Objects.equals(privatePasswords.get(name), password)
                && joinChannel(player, name);
    }

    public Set<UUID> getChannelMembers(String channel) {
        return Set.copyOf(channelMembers.getOrDefault(channel, Collections.emptySet()));
    }

    public boolean isChannelMuted(String channel, UUID speaker) {
        return channelMuted.getOrDefault(channel, Collections.emptySet()).contains(speaker);
    }

    public void toggleChannelMute(String channel, UUID speaker) {
        Set<UUID> set = channelMuted.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(speaker)) set.remove(speaker);
    }

    public boolean kickFromChannel(String channel, UUID target) {
        Set<UUID> members = channelMembers.get(channel);
        if (members == null || !members.remove(target)) return false;
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) joinDefaultChannel(targetPlayer);
        return true;
    }

    public boolean isChannelOwner(Player player) {
        return player.getUniqueId().equals(privateOwners.get(getChannel(player)));
    }

    public String getChannel(Player player) {
        return getChannel(player.getUniqueId());
    }

    public String getChannel(UUID uuid) {
        String channel = channels.computeIfAbsent(uuid, ignored ->
                plugin.getConfig().getString("voice.default-channel", "General"));
        channelMembers.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet()).add(uuid);
        return channel;
    }

    public void joinChannelLegacy(Player player, String channel) {
        if (channel == null || channel.isBlank()) return;
        channels.put(player.getUniqueId(), channel.trim());
        refreshRoute(player);
    }

    public void joinDefaultChannel(Player player) {
        String defaultChannel = plugin.getConfig().getString("voice.default-channel", "General");
        joinChannel(player, defaultChannel);
    }

    public void leaveChannel(Player player) {
        joinDefaultChannel(player);
    }

    public boolean isMicMuted(Player player) {
        return isMicMuted(player.getUniqueId());
    }

    public boolean isMicMuted(UUID uuid) {
        return micMuted.getOrDefault(uuid, false);
    }

    public boolean toggleMic(Player player) {
        return toggleMic(player.getUniqueId());
    }

    public boolean toggleMic(UUID uuid) {
        boolean value = !isMicMuted(uuid);
        micMuted.put(uuid, value);
        refreshRoute(Bukkit.getPlayer(uuid));
        return value;
    }

    public boolean isOutputMuted(Player player) {
        return isOutputMuted(player.getUniqueId());
    }

    public boolean isOutputMuted(UUID uuid) {
        return outputMuted.getOrDefault(uuid, false);
    }

    public boolean toggleOutput(Player player) {
        return toggleOutput(player.getUniqueId());
    }

    public boolean toggleOutput(UUID uuid) {
        boolean value = !isOutputMuted(uuid);
        outputMuted.put(uuid, value);
        refreshRoute(Bukkit.getPlayer(uuid));
        return value;
    }

    public double getVolume(Player player) {
        return playerVolumes.getOrDefault(player.getUniqueId(),
                plugin.getConfig().getDouble("voice.default-volume", 1.0));
    }

    public void setVolume(Player player, double volume) {
        playerVolumes.put(player.getUniqueId(), Math.max(0.0, Math.min(2.0, volume)));
    }

    public double getRange(Player player) {
        return ranges.getOrDefault(player.getUniqueId(),
                plugin.getConfig().getDouble("voice.default-range", 32.0));
    }

    public void setRange(Player player, double range) {
        double max = plugin.getConfig().getDouble("voice.max-range", 96.0);
        ranges.put(player.getUniqueId(), Math.max(4.0, Math.min(max, range)));
        refreshRoute(player);
    }

    public boolean isMuted(Player viewer, Player target) {
        return isMuted(viewer.getUniqueId(), target.getUniqueId());
    }

    public boolean isMuted(UUID viewer, UUID target) {
        return mutedPlayers.getOrDefault(viewer, Collections.emptySet()).contains(target);
    }

    public void toggleMute(Player viewer, Player target) {
        toggleMute(viewer.getUniqueId(), target.getUniqueId());
    }

    public void toggleMute(UUID viewer, UUID target) {
        Set<UUID> set = mutedPlayers.computeIfAbsent(viewer, ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(target)) set.remove(target);
        Player player = Bukkit.getPlayer(viewer);
        if (player != null) refreshRoute(player);
    }

    public boolean canHear(Player listener, Player speaker) {
        VoiceRoute listenerRoute = routes.get(listener.getUniqueId());
        VoiceRoute speakerRoute = routes.get(speaker.getUniqueId());
        return listenerRoute != null && speakerRoute != null && listenerRoute.canHear(speakerRoute)
                && !isChannelMuted(speakerRoute.channel(), speakerRoute.uuid());
    }

    public VoiceRoute getRoute(UUID uuid) {
        return routes.get(uuid);
    }

    public void setConnected(UUID uuid, boolean value) {
        connected.put(uuid, value);
    }

    public boolean isConnected(UUID uuid) {
        return connected.getOrDefault(uuid, false);
    }

    public void markSpeaking(UUID uuid) {
        speakingUntil.put(uuid, System.currentTimeMillis() + 650L);
    }

    public boolean isSpeaking(UUID uuid) {
        return speakingUntil.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    public int countSpeaking(Player viewer) {
        VoiceRoute viewerRoute = routes.get(viewer.getUniqueId());
        if (viewerRoute == null) return 0;

        int count = 0;
        for (VoiceRoute route : routes.values()) {
            if (!route.uuid().equals(viewer.getUniqueId()) && isSpeaking(route.uuid())
                    && viewerRoute.canHear(route) && !isChannelMuted(route.channel(), route.uuid())) count++;
        }
        return count;
    }

    private void refreshRoutes() {
        for (Player player : Bukkit.getOnlinePlayers()) refreshRoute(player);
    }

    private void refreshRoute(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        routes.put(uuid, new VoiceRoute(
                uuid,
                player.getWorld().getUID(),
                player.getX(),
                player.getY(),
                player.getZ(),
                getChannel(uuid),
                isMicMuted(uuid),
                isOutputMuted(uuid),
                getRange(player),
                Set.copyOf(mutedPlayers.getOrDefault(uuid, Collections.emptySet()))
        ));
    }

    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        String channel = channels.get(uuid);
        if (channel != null) {
            Set<UUID> members = channelMembers.get(channel);
            if (members != null) members.remove(uuid);
        }
        for (Set<UUID> members : channelMembers.values()) members.remove(uuid);
        for (Set<UUID> muted : channelMuted.values()) muted.remove(uuid);
        channels.remove(uuid);
        mutedPlayers.remove(uuid);
        playerVolumes.remove(uuid);
        micMuted.remove(uuid);
        outputMuted.remove(uuid);
        ranges.remove(uuid);
        routes.remove(uuid);
        speakingUntil.remove(uuid);
        connected.remove(uuid);
    }

    public void shutdown() {
        channels.clear();
        mutedPlayers.clear();
        playerVolumes.clear();
        micMuted.clear();
        outputMuted.clear();
        ranges.clear();
        routes.clear();
        speakingUntil.clear();
        connected.clear();
        privateOwners.clear();
        privatePasswords.clear();
        channelMembers.clear();
        channelMuted.clear();
    }
}