# Minecraft-VoiceChannel Wiki

> Cross-platform voice chat for Paper 26.2 + Java 25, with Java and Bedrock clients.

## Overview

Minecraft-VoiceChannel keeps the Minecraft server responsible for identity, permissions, channels, proximity and moderation, while companion clients handle microphone capture and speaker playback.

### Highlights

| Component | Purpose |
|---|---|
| Paper plugin | UI, permissions, channels, moderation, proximity routing |
| TCP gateway | Authenticated voice transport |
| Java client | Microphone capture and playback |
| Android client | Bedrock companion voice client |
| Floodgate | Bedrock identity and forms |

## Network

**Default voice gateway: TCP 26467**

The gateway does not require FFmpeg, JAVE2, glibc binaries or native Linux executables.

## Quick start

1. Install the Paper plugin.
2. Keep Floodgate installed for Bedrock support.
3. Start the server once.
4. Expose TCP port 26467, or use your hosting provider's secondary TCP port.
5. Open /voicechannel.
6. Choose Pair Device.
7. Enter the one-time code in the voice client.
8. Connect and speak.

## Documentation

- [Installation](Installation.md)
- [Configuration](Configuration.md)
- [Clients](Clients.md)
- [Protocol](Protocol.md)
- [Architecture](Architecture.md)
- [Troubleshooting](Troubleshooting.md)

## Important limitation

A normal Paper plugin cannot access a player's microphone. A companion client is required for actual voice capture and playback.

## UI rule

Minecraft-facing UI and protocol messages intentionally avoid emoji characters for Java and Bedrock rendering compatibility.
