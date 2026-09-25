# Minecraft VoiceChannel Android Voice Client

Companion Android client for Bedrock players.

It captures the Android microphone and plays incoming voice frames through the Minecraft-VoiceChannel gateway on TCP 26467.

This is a companion voice client, not a Bedrock resource pack. Geyser/Floodgate cannot provide microphone access to a Paper plugin.

Connection fields:
- Gateway host
- Gateway port (default 26467)
- Minecraft UUID
- One-time pairing code (run /voicechannel pair in Minecraft)

Audio format: 16 kHz, 16-bit, mono PCM, 20 ms frames.

Pairing codes expire after 120 seconds and are single-use.
