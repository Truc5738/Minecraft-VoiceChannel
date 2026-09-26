# Voice Clients

## Why a companion client is required

Paper plugins do not receive raw microphone input from normal Minecraft clients.

The server plugin therefore handles voice policy while a companion client handles:

- microphone capture
- PCM frame generation
- TCP transport
- received audio playback

## Java client

The Java client uses the standard Java Sound API.

Audio format:

- 16,000 Hz
- 16-bit
- mono
- little-endian PCM
- 20 ms per frame
- 640 bytes per frame

Pairing mode:

~~~text
java -jar voice-client.jar <host> <port> pair <6-digit-code>
~~~

The client validates frame sizes, packet magic, packet types and per-speaker sequence numbers.

## Android Bedrock client

The Android client uses:

- AudioRecord for microphone capture
- AudioTrack for playback
- Kotlin networking
- cached session credentials
- one-time pairing
- automatic reconnect
- microphone permission checks

Default port: **26467**

The app can reconnect using the cached session token. If the server rejects an expired session, the app clears the cached token and requests a new pairing code.

## Pairing flow

~~~text
Minecraft player
      |
      | /voicechannel
      v
Paper plugin
      |
      | one-time pair code
      v
Voice client
      |
      | PAIR:<code>
      v
TCP gateway
      |
      | SESSION:<token>
      v
Voice client
~~~

## Audio flow

~~~text
Microphone -> Voice client -> TCP gateway -> server routing -> listeners
                                                               |
                                                               v
                                                           Speaker
~~~

No FFmpeg or native Linux audio binary is required.
