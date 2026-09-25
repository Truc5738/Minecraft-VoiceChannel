package dev.truc5738.voicechannel;

import java.util.Set;
import java.util.UUID;

public record VoiceRoute(
        UUID uuid,
        UUID worldId,
        double x,
        double y,
        double z,
        String channel,
        boolean micMuted,
        boolean outputMuted,
        double range,
        Set<UUID> mutedPlayers
) {
    public boolean canHear(VoiceRoute speaker) {
        if (uuid.equals(speaker.uuid())) return true;
        if (outputMuted) return false;
        if (!channel.equals(speaker.channel())) return false;
        if (!worldId.equals(speaker.worldId())) return false;
        if (mutedPlayers.contains(speaker.uuid())) return false;

        double dx = x - speaker.x();
        double dy = y - speaker.y();
        double dz = z - speaker.z();
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
