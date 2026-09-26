# Configuration

## Gateway

~~~yaml
gateway:
  host: 0.0.0.0
  port: 26467
~~~

### host

Local bind address. 0.0.0.0 listens on all available interfaces and is suitable for most Minecraft hosts.

### port

TCP port used by the voice gateway.

Default: **26467**

Use your host's assigned secondary TCP port when required.

## Gateway token

On first startup, the placeholder token is replaced by a random 256-bit URL-safe token and saved by the plugin.

Do not share it publicly.

## Runtime protection

The gateway uses:

- TCP_NODELAY
- TCP keepalive
- socket read timeout
- PING/PONG heartbeats
- authenticated sessions
- bounded packet sizes
- sequence validation

A session that stops responding to heartbeats is removed.

## Voice routing

Before forwarding an audio frame, the server checks:

- authenticated connection
- microphone mute state
- output mute state
- current channel
- channel mute state
- same world
- proximity range
- listener mute
- per-player volume

## Private channels

Private-channel ownership follows membership. If the owner leaves, ownership can transfer to another remaining member. Empty private channels are cleaned up.

## UI

Java players use an inventory interface.

Bedrock players use Floodgate/Cumulus forms.

Both interfaces expose the same core voice controls.
