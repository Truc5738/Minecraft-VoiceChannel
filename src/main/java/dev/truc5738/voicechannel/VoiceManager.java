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
        if (configured == null || configured.isBlank()) configured = "General";

        channelMembers.putIfAbsent(configured.trim(), ConcurrentHashMap.newKeySet());
        if (plugin.getConfig().getConfigurationSection("channels") != null) {
            for (String name : plugin.getConfig().getConfigurationSection("channels").getKeys(false)) {
                if (name != null && !name.isBlank()) {
                    channelMembers.putIfAbsent(name.trim(), ConcurrentHashMap.newKeySet());
                }
            }
        }

        // The default channel must always be joinable by the normal leave/reset flow.
        if (privateOwners.containsKey(configured.trim())) {
            privateOwners.remove(configured.trim());
            privatePasswords.remove(configured.trim());
        }
    }

    private String defaultChannel() {
        String configured = plugin.getConfig().getString("voice.default-channel", "General");
        if (configured == null || configured.isBlank()) return "General";
        String target = configured.trim();
        if (!channelMembers.containsKey(target) || isPrivateChannel(target)) {
            channelMembers.putIfAbsent("General", ConcurrentHashMap.newKeySet());
            return "General";
        }
        return target;
    }

    public Set<String> getChannels() {
        return Set.copyOf(channelMembers.keySet());
    }

    public Set<String> getPublicChannels() {
        Set<String> publicChannels = ConcurrentHashMap.newKeySet();
        for (String channel : channelMembers.keySet()) {
            if (!privateOwners.containsKey(channel)) publicChannels.add(channel);
        }
        return Set.copyOf(publicChannels);
    }

    public boolean isPrivateChannel(String channel) {
        return channel != null && privateOwners.containsKey(channel.trim());
    }

    public boolean joinChannel(Player player, String channel) {
        if (channel == null || channel.isBlank()) return false;
        String target = channel.trim();
        if (isPrivateChannel(target)) return false;
        if (!channelMembers.containsKey(target)) return false;

        String current = getChannel(player);
        channelMembers.computeIfAbsent(current, ignored -> ConcurrentHashMap.newKeySet()).remove(player.getUniqueId());
        channelMembers.computeIfAbsent(target, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        channels.put(player.getUniqueId(), target);
        refreshRoute(player);
        return true;
    }

    public boolean createPrivateChannel(Player owner, String name, String password) {
        if (name == null || name.isBlank() || password == null || password.isBlank()) return false;
        String target = name.trim();
        if (target.length() > 24 || target.contains(" ") || channelMembers.containsKey(target) || privateOwners.containsKey(target)) return false;

        if (channelMembers.putIfAbsent(target, ConcurrentHashMap.newKeySet()) != null) return false;
        if (privateOwners.putIfAbsent(target, owner.getUniqueId()) != null) {
            channelMembers.remove(target);
            return false;
        }
        privatePasswords.put(target, password);
        if (!joinPrivateChannel(owner, target, password)) {
            privateOwners.remove(target, owner.getUniqueId());
            privatePasswords.remove(target);
            channelMembers.remove(target);
            return false;
        }
        return true;
    }

    public boolean joinPrivateChannel(Player player, String name, String password) {
        if (name == null || password == null || password.isBlank()) return false;
        String target = name.trim();
        if (!privateOwners.containsKey(target)) return false;
        if (!java.util.Objects.equals(privatePasswords.get(target), password)) return false;

        String current = getChannel(player);
        channelMembers.computeIfAbsent(current, ignored -> ConcurrentHashMap.newKeySet()).remove(player.getUniqueId());
        channelMembers.computeIfAbsent(target, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        channels.put(player.getUniqueId(), target);
        refreshRoute(player);
        return true;
    }

    public Set<UUID> getChannelMembers(String channel) {
        return Set.copyOf(channelMembers.getOrDefault(channel, Collections.emptySet()));
    }

    public boolean isChannelMuted(String channel, UUID speaker) {
        return channelMuted.getOrDefault(channel, Collections.emptySet()).contains(speaker);
    }

    public boolean isMemberOfChannel(String channel, UUID player) {
        if (channel == null || player == null) return false;
        return channelMembers.getOrDefault(channel.trim(), Collections.emptySet()).contains(player);
    }

    public boolean toggleChannelMute(String channel, UUID speaker) {
        if (!isMemberOfChannel(channel, speaker)) return false;
        Set<UUID> set = channelMuted.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(speaker)) set.remove(speaker);
        return true;
    }

    public boolean kickFromChannel(String channel, UUID target) {
        Set<UUID> members = channelMembers.get(channel);
        if (members == null || !members.remove(target)) return false;

        if (privateOwners.get(channel) != null && privateOwners.get(channel).equals(target)) {
            transferPrivateOwnership(channel, members);
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) joinDefaultChannel(targetPlayer);
        cleanupPrivateChannelIfEmpty(channel);
        return true;
    }

    public boolean isChannelOwner(Player player) {
        return player.getUniqueId().equals(privateOwners.get(getChannel(player)));
    }

    public String getChannel(Player player) {
        return getChannel(player.getUniqueId());
    }

    public String getChannel(UUID uuid) {
        String channel = channels.computeIfAbsent(uuid, ignored -> defaultChannel());
        if (!channelMembers.containsKey(channel) || (isPrivateChannel(channel) && !privateOwners.containsKey(channel))) {
            channel = defaultChannel();
            channels.put(uuid, channel);
        }
        channelMembers.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet()).add(uuid);
        return channel;
    }

    public void joinChannelLegacy(Player player, String channel) {
        if (channel == null || channel.isBlank()) return;
        joinChannel(player, channel);
    }

    public void joinDefaultChannel(Player player) {
        joinChannel(player, defaultChannel());
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
        return getVolume(player.getUniqueId());
    }

    public double getVolume(UUID uuid) {
        double configured = plugin.getConfig().getDouble("voice.default-volume", 1.0);
        configured = Math.max(0.0, Math.min(2.0, configured));
        return playerVolumes.getOrDefault(uuid, configured);
    }

    public void setVolume(Player player, double volume) {
        playerVolumes.put(player.getUniqueId(), Math.max(0.0, Math.min(2.0, volume)));
    }

    public double getRange(Player player) {
        return getRange(player.getUniqueId());
    }

    public double getRange(UUID uuid) {
        double configured = plugin.getConfig().getDouble("voice.default-range", 32.0);
        double max = Math.max(4.0, plugin.getConfig().getDouble("voice.max-range", 96.0));
        double value = ranges.getOrDefault(uuid, configured);
        return Math.max(4.0, Math.min(max, value));
    }

    public void setRange(Player player, double range) {
        double max = Math.max(4.0, plugin.getConfig().getDouble("voice.max-range", 96.0));
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

    private void transferPrivateOwnership(String channel, Set<UUID> members) {
        if (channel == null || !privateOwners.containsKey(channel)) return;
        if (members == null || members.isEmpty()) {
            privateOwners.remove(channel);
            privatePasswords.remove(channel);
            return;
        }

        UUID nextOwner = members.stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .findFirst()
                .orElse(members.iterator().next());
        privateOwners.put(channel, nextOwner);
    }

    private void cleanupPrivateChannelIfEmpty(String channel) {
        if (channel == null || !privateOwners.containsKey(channel)) return;
        Set<UUID> members = channelMembers.get(channel);
        if (members != null && !members.isEmpty()) return;

        privateOwners.remove(channel);
        privatePasswords.remove(channel);
        channelMembers.remove(channel);
        channelMuted.remove(channel);
    }

    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        String channel = channels.get(uuid);
        if (channel != null) {
            Set<UUID> members = channelMembers.get(channel);
            if (members != null) {
                boolean wasOwner = uuid.equals(privateOwners.get(channel));
                members.remove(uuid);
                if (wasOwner) transferPrivateOwnership(channel, members);
            }
            cleanupPrivateChannelIfEmpty(channel);
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
