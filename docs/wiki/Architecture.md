# Architecture

## Components

~~~text
                 +----------------------+
                 |     Paper 26.2       |
                 | VoiceChannelPlugin   |
                 +----------+-----------+
                            |
             +--------------+--------------+
             |                             |
       VoiceManager                    VoiceMenu
             |                             |
             +--------------+--------------+
                            |
                     VoiceGateway
                            |
                     TCP : 26467
                    /             \
                   /               \
          Java voice client    Android client
             microphone          microphone
             speaker             speaker
~~~

## VoiceManager

Maintains runtime voice policy:

- channels
- membership
- mute state
- microphone state
- output state
- ranges
- volumes
- private channels
- speaking state
- connection state

## VoiceGateway

Maintains:

- TCP listener
- authenticated sessions
- pairing codes
- session credentials
- heartbeats
- packet validation
- audio forwarding

## VoiceMenu

Provides:

- microphone control
- output control
- channel selection
- range
- volume
- player mute
- private channels
- moderation
- settings
- device pairing

## Design goals

### Alpine-safe

The gateway uses Java networking APIs only. It does not require FFmpeg, JAVE2, glibc, shell commands or native Linux executables.

### Cross-platform

Java and Bedrock clients use the same authenticated gateway and binary protocol.

### Server-authoritative routing

Clients do not decide who should hear a voice frame. The server evaluates the route before forwarding audio.

### Reconnect-safe sessions

A reconnecting player can reuse a valid session credential. A newer connection replaces an older connection, and stale cleanup is guarded so the old connection cannot mark the new connection offline.

## Security

A client must authenticate before audio is accepted.

Pairing codes are short-lived and one-time. Session credentials have a finite lifetime. Malformed frames are rejected before routing.
