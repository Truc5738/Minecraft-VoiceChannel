# Troubleshooting

## Client cannot connect

Check:

1. Server is running.
2. Voice gateway started.
3. TCP port is exposed.
4. Client host is correct.
5. Client port matches the server.
6. Pairing code is still valid.
7. Firewall rules allow the connection.

Default: **TCP 26467**

## Geyser works but voice does not

Geyser and the voice gateway are separate services.

The voice gateway uses TCP 26467. It does not replace Geyser's Bedrock UDP port.

## Pairing rejected

Generate a new code.

Pairing codes are six digits, short-lived and one-time use.

## Session expired

The Android client clears a rejected cached session and asks for a new pairing code.

Pair the device again.

## Connected but no audio

Check:

- microphone permission
- microphone mute
- output mute
- current voice channel
- channel mute
- voice range
- player mute
- same Minecraft world
- listener volume

## Android microphone fails

Android requires microphone permission.

Open Android settings, allow microphone access for the voice client, then reconnect.

The client also verifies AudioRecord initialization.

## Android speaker fails

The client verifies AudioTrack initialization.

If another application restricts audio output, close it and retry.

## Reconnect loop

The Android client retries after unexpected connection loss.

The first retry waits about 3 seconds. Later retries use about 2 seconds.

Authentication failures stop reconnect and request a new pairing code.

## Hosting limitations

If the hosting provider does not expose an inbound TCP port, direct client connections to the embedded gateway cannot work.

Use an accessible secondary TCP port or another network architecture.

## Alpine Linux

The gateway is designed for Alpine/musl environments because it uses standard Java networking and does not launch native FFmpeg/JAVE2 binaries.
