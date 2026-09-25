# Minecraft VoiceChannel Voice Client

Experimental desktop Java voice client for the Minecraft-VoiceChannel gateway.

## Requirements

- Java 25
- A microphone and speaker
- A Minecraft player UUID
- The gateway token from the server's config.yml

## Start

java -jar Minecraft-VoiceChannel-VoiceClient.jar <host> 26467 <player-uuid> <gateway-token>

Audio is 16 kHz, 16-bit, mono PCM in 20 ms frames.

This client is intentionally independent of FFmpeg, JAVE2 and native Linux binaries.

The Bedrock Android client is a separate component and cannot be implemented as a Paper plugin alone because Android microphone access belongs to the companion client.


## Java player setup

1. Join the Minecraft server.
2. Run `/voicechannel pair`.
3. Start the client with:
   `java -jar voice-client.jar <gateway-host> 26467 pair <6-digit-code>`
4. The pair code is single-use and expires after 120 seconds.

The client captures the microphone and plays incoming voice frames. Java and Android/Bedrock clients use the same 16 kHz mono PCM voice frame format, so Java and Bedrock players can talk through the same gateway.
