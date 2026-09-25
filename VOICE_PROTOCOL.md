# Minecraft VoiceChannel Client Protocol

This document defines the client-side protocol required for Java and Bedrock voice clients.

## Transport

- TCP
- Default port: 26467
- The server plugin does not require FFmpeg, JAVE2, glibc, or a native Linux executable.
- One persistent TCP connection is used per voice client.
- All integers are big-endian.
- Packet header size is 29 bytes.

## Packet format

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

## HELLO

Type `1`.

The UUID normally identifies the authenticated Minecraft player. Sequence must be `0`.

For Bedrock pairing, the client sends an all-zero UUID (`00000000-0000-0000-0000-000000000000`) and a UTF-8 payload of `PAIR:<6-digit-code>`. The gateway resolves the pairing code to the Minecraft UUID, consumes the code, and binds the connection to that player.

For token authentication, the payload is the UTF-8 gateway token from `plugins/Minecraft-VoiceChannel/config.yml`.

For reconnects, a client may use the UTF-8 payload `SESSION:<session-token>`. Session credentials are issued by a successful HELLO response and expire after 24 hours.

A successful HELLO response is another type `1` packet. Its UUID is the assigned Minecraft UUID and its UTF-8 payload is:

```
OK
SESSION:<session-token>
```

A rejected HELLO response uses a UTF-8 payload beginning with `ERROR:`, for example `ERROR:AUTH` or `ERROR:PLAYER_OFFLINE`.

## AUDIO

Type `2`.

The UUID identifies the speaker. The current implementation uses **raw PCM16 little-endian audio**, not a compressed codec:

- Sample rate: 16,000 Hz
- Channels: mono
- Sample format: signed 16-bit PCM, little-endian
- Frame duration: 20 ms
- Samples per frame: 320
- Normal frame payload: 640 bytes

Java and Bedrock clients must use this exact PCM format for interoperability.

The sequence number starts at `0` for each client connection and must increase strictly. The gateway drops duplicate or out-of-order audio frames.

The gateway applies the listener's configured output volume before forwarding the frame.

## PING / PONG

### PING

Type `4`.

The server periodically sends an empty PING packet to each connected client. The sequence is a server heartbeat sequence.

### PONG

Type `5`.

The client must respond to PING with an empty PONG packet using the same sequence number. The UUID must be the assigned Minecraft UUID.

The gateway considers a session stale when it has not received a PONG for more than 15 seconds.

## GOODBYE

Type `3`.

The payload may be empty. The client sends GOODBYE when it intentionally disconnects.

## Server routing

An AUDIO frame is forwarded only when:

1. the speaker's voice session is still the active session for that UUID;
2. the speaker is connected and microphone-unmuted;
3. the speaker and listener are in compatible voice channels;
4. they are in the same world;
5. the listener has not muted the speaker;
6. the listener's output is not muted;
7. the listener is within the configured voice range.

The speaker does not receive their own AUDIO frame.

## Java client

A Java voice client/mod must:

1. capture microphone audio as PCM16 16 kHz mono;
2. connect to TCP port 26467;
3. send HELLO;
4. accept the assigned UUID from the successful HELLO response;
5. send 20 ms AUDIO frames;
6. answer server PING packets with PONG;
7. play incoming AUDIO frames;
8. send GOODBYE when stopping.

The repository includes a standalone Java reference client under `voice-client/`.

## Bedrock client

Geyser/Floodgate alone cannot expose a Bedrock device microphone to a Paper plugin. A companion Bedrock voice client is required.

The Android reference client uses the one-time pairing code from `/voicechannel pair`, then stores the returned session credential for reconnects. If a fresh 6-digit pairing code is entered, it takes precedence over the cached session credential so the user can deliberately pair the device again.

The in-game `/voicechannel` UI remains the control surface for channels, moderation, volume, range, and pairing. The companion client provides microphone/audio I/O.

## Security

Never publish the gateway token. If it is compromised, generate a new token.

Session credentials are scoped to a Minecraft UUID and expire after 24 hours. Pairing codes are single-use and expire after 120 seconds.

For public servers, protect TCP 26467 with firewall rules and preferably place the gateway behind secure encrypted transport. The current protocol itself is plain TCP and does not provide encryption.

