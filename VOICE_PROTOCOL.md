# Minecraft VoiceChannel Client Protocol

This document defines the client side required for Java and Bedrock voice clients.

## Transport

- TCP
- Default port: 26467
- The server plugin does not require FFmpeg, JAVE2, glibc, or a native Linux executable.
- One persistent connection is used per voice client.

## Packet format

All integers are big-endian.

Each packet contains:

| Field | Size |
|---|---:|
| Magic | 4 bytes |
| Type | 1 byte |
| UUID | 16 bytes |
| Sequence | 4 bytes |
| Payload length | 4 bytes |
| Payload | N bytes |

Magic is `0x4D564331` (`MVC1`).

### HELLO

Type `1`.

The UUID normally identifies the authenticated Minecraft player. Sequence is `0`.

For Bedrock pairing, the client may send an all-zero UUID (`00000000-0000-0000-0000-000000000000`) and a payload of `PAIR:<6-digit-code>`. The gateway resolves the pairing code to the Minecraft UUID, consumes the code, and binds the connection to that player.

For token authentication, the UUID must identify the authenticated Minecraft player. Payload is the UTF-8 gateway token from `plugins/Minecraft-VoiceChannel/config.yml`.

### AUDIO

Type `2`.

The UUID identifies the speaker. The payload is an encoded voice frame. Java and Bedrock clients must use the same codec and frame format.

### GOODBYE

Type `3`.

The payload may be empty.

## Server routing

An AUDIO frame is forwarded only when the listener is connected, the players can hear each other according to channel/proximity rules, and the speaker is not muted.

## Java client

A Java voice client/mod must capture microphone audio, encode frames, connect to TCP 26467, send HELLO, send AUDIO frames, and decode incoming AUDIO frames to the selected output device.

## Bedrock client

Geyser/Floodgate alone cannot expose a Bedrock device microphone to a Paper plugin. A companion Bedrock voice client is required. It authenticates through the one-time pairing code from `/voicechannel pair`, so the user does not need to enter the Minecraft UUID manually. It captures microphone audio and implements this protocol.

The in-game `/voicechannel` UI remains the control surface for channels and moderation; the companion client provides microphone/audio I/O.

## Security

Never publish the gateway token. If it is compromised, generate a new token. For public servers, protect the gateway with firewall rules and preferably a secure encrypted transport.
