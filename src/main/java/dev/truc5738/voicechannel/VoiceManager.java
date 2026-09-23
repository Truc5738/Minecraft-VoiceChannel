package dev.truc5738.voicechannel;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoiceManager {
    private final JavaPlugin plugin;
    private final Map<UUID, String> channels = new HashMap<>();
    private final Map<UUID, Set<UUID>> mutedPlayers = new HashMap<>();
    private final Map<UUID, Double> playerVolumes = new HashMap<>();
    private final Map<UUID, Boolean> micMuted = new HashMap<>();
    private final Map<UUID, Boolean> outputMuted = new HashMap<>();
    private final Map<UUID, Double> ranges = new HashMap<>();

    public VoiceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getChannel(Player player) {
        return channels.computeIfAbsent(player.getUniqueId(), ignored ->
                plugin.getConfig().getString("voice.default-channel", "General"));
    }

    public void joinChannel(Player player, String channel) {
        channels.put(player.getUniqueId(), channel);
    }

    public void leaveChannel(Player player) {
        channels.remove(player.getUniqueId());
    }

    public boolean isMicMuted(Player player) {
        return micMuted.getOrDefault(player.getUniqueId(), false);
    }

    public boolean toggleMic(Player player) {
        boolean value = !isMicMuted(player);
        micMuted.put(player.getUniqueId(), value);
        return value;
    }

    public boolean isOutputMuted(Player player) {
        return outputMuted.getOrDefault(player.getUniqueId(), false);
    }

    public boolean toggleOutput(Player player) {
        boolean value = !isOutputMuted(player);
        outputMuted.put(player.getUniqueId(), value);
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
    }

    public boolean isMuted(Player viewer, Player target) {
        return mutedPlayers.getOrDefault(viewer.getUniqueId(), Collections.emptySet())
                .contains(target.getUniqueId());
    }

    public void toggleMute(Player viewer, Player target) {
        Set<UUID> set = mutedPlayers.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());
        if (!set.add(target.getUniqueId())) {
            set.remove(target.getUniqueId());
        }
    }

    public boolean canHear(Player listener, Player speaker) {
        if (listener.equals(speaker)) return true;
        if (isOutputMuted(listener) || isMuted(listener, speaker)) return false;
        if (!getChannel(listener).equals(getChannel(speaker))) return false;
        if (isMicMuted(speaker)) return false;
        return listener.getWorld().equals(speaker.getWorld())
                && listener.getLocation().distanceSquared(speaker.getLocation()) <= getRange(listener) * getRange(listener);
    }

    public void shutdown() {
        channels.clear();
        mutedPlayers.clear();
        playerVolumes.clear();
        micMuted.clear();
        outputMuted.clear();
        ranges.clear();
    }
}
