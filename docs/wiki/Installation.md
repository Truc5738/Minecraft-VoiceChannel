# Installation

## Requirements

### Server

- Paper 26.2
- Java 25
- Floodgate for Bedrock integration
- One reachable inbound TCP port
- Recommended voice port: 26467

### Client

- Java desktop voice client, or
- Android Bedrock companion client

## 1. Install the plugin

Copy the generated plugin JAR into the server plugins directory and restart Paper.

The plugin creates its configuration and starts the embedded voice gateway.

## 2. Configure the gateway

Open:

~~~text
plugins/Minecraft-VoiceChannel/config.yml
~~~

The normal gateway values are:

~~~yaml
gateway:
  host: 0.0.0.0
  port: 26467
~~~

Keep the generated configuration structure intact.

## 3. Open the TCP port

Your host must allow inbound TCP traffic to the configured gateway port.

Do not confuse this with Geyser's Bedrock UDP port.

If your host provides a secondary TCP port, use that value consistently on both server and clients.

## 4. Pair a device

Run:

~~~text
/voicechannel
~~~

Choose **Pair Device**.

The server generates a six-digit pairing code. It expires after 120 seconds and can only be used once.

Enter the code in the voice client and connect.

## 5. Verify

A successful connection should show the client as connected.

If it repeatedly reconnects, check the host, TCP port, firewall rules and pairing code.

## Security

- Pair codes expire after 120 seconds.
- Pair codes are one-time use.
- Session credentials expire after 24 hours.
- A newer connection replaces an older connection for the same player.
- The placeholder gateway token is replaced by a random 256-bit token on startup.

Never publish session credentials or gateway secrets.
