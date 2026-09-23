# Minecraft-VoiceChannel

A Paper plugin project for a cross-platform voice channel system for Java and Bedrock/PE players.

## Target

- Paper 1.26.x
- Java 25
- Java Edition clients
- Bedrock/PE clients through a compatible bridge/client voice transport
- Main command: `/voicechannel`
- UI text intentionally contains no emoji characters

## UI features

1. Microphone mute/unmute
2. Voice output mute/unmute
3. Live speaking player list
4. Voice channel join/leave
5. Private channels
6. Proximity voice range
7. Per-player volume
8. Player mute
9. Channel moderation
10. Voice settings

## Architecture

The Paper plugin handles player identity, permissions, channels, UI state, moderation, proximity rules and the voice gateway protocol. Minecraft clients do not expose microphone input to a normal Paper plugin, so actual audio capture and playback are handled by a separate compatible voice client/bridge and gateway.

## Development status

Initial implementation scaffold. The next stage is the gateway protocol and client bridge implementation.
