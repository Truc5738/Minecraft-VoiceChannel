# Voice Protocol

The transport is a binary TCP framing protocol.

## Frame layout

| Field | Size |
|---|---:|
| Magic | 4 bytes |
| Type | 1 byte |
| UUID | 16 bytes |
| Sequence | 4 bytes |
| Payload length | 4 bytes |
| Payload | N bytes |

All integer fields use big-endian network byte order.

Magic:

~~~text
MVC1
~~~

Numeric value: **0x4D564331**

## Packet types

| Type | Name | Payload |
|---:|---|---|
| 1 | HELLO | authentication credential |
| 2 | AUDIO | 640-byte PCM16 frame |
| 3 | GOODBYE | empty |
| 4 | PING | empty |
| 5 | PONG | empty |

## Audio format

- PCM16
- little-endian samples
- 16 kHz
- mono
- 20 ms
- 320 samples
- 640 bytes

## Authentication

HELLO can use:

~~~text
PAIR:<6-digit-code>
SESSION:<session-token>
~~~

The server returns an acknowledgement containing the assigned player UUID and session token.

The UUID attached to an authenticated session is authoritative.

## Sequence numbers

AUDIO sequence numbers are unsigned 32-bit counters.

Clients compare them using unsigned ordering so rollover does not permanently stop audio.

Received AUDIO sequence state is tracked independently for each speaker.

## Validation

The gateway rejects malformed traffic such as:

- invalid magic
- oversized payload
- AUDIO payload not equal to 640 bytes
- AUDIO before authentication
- wrong session UUID
- stale or out-of-order audio
- invalid packet lengths
- invalid authentication

## Heartbeat

The gateway sends periodic PING packets.

Clients reply with PONG.

A session that stops responding within the heartbeat timeout is removed.

## Routing

Valid AUDIO is forwarded only when the listener passes voice route checks for:

- authentication
- channel
- world
- range
- listener mute
- output mute
- speaker mute
- channel mute
- volume
