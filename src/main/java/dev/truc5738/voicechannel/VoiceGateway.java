package dev.truc5738.voicechannel;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class VoiceGateway {
    private static final int MAX_FRAME_SIZE = 16 * 1024;
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1;
    private static final byte AUDIO = 2;
    private static final byte GOODBYE = 3;
    private static final byte PING = 4;
    private static final byte PONG = 5;

    private final JavaPlugin plugin;
    private final VoiceManager manager;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Minecraft-VoiceChannel-Gateway");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Minecraft-VoiceChannel-Heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private String token;
    private final Map<String, Pairing> pairings = new ConcurrentHashMap<>();
    private final Map<String, SessionCredential> credentials = new ConcurrentHashMap<>();
    private final Map<UUID, String> credentialByPlayer = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public VoiceGateway(JavaPlugin plugin, VoiceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public boolean start() {
        if (!plugin.getConfig().getBoolean("voice.gateway.enabled", false)) return false;

        token = plugin.getConfig().getString("voice.gateway.token", "");
        if (token == null || token.isBlank() || token.equals("change-me")) {
            token = generateToken();
            plugin.getConfig().set("voice.gateway.token", token);
            plugin.saveConfig();
            plugin.getLogger().info("Generated a new voice gateway token and saved it to config.yml.");
        }

        String host = plugin.getConfig().getString("voice.gateway.host", "0.0.0.0");
        int port = plugin.getConfig().getInt("voice.gateway.port", 26467);

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(host, port));
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not start voice gateway on " + host + ":" + port + ": " + exception.getMessage());
            return false;
        }

        running = true;
        acceptThread = new Thread(this::acceptLoop, "Minecraft-VoiceChannel-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
        heartbeat.scheduleAtFixedRate(this::heartbeat, 5, 5, TimeUnit.SECONDS);

        plugin.getLogger().info("Voice gateway listening on TCP " + host + ":" + port + ".");
        plugin.getLogger().info("Gateway uses JVM networking only; no FFmpeg, JAVE2, glibc or native Linux binary is required.");
        return true;
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        for (Session session : new ArrayList<>(sessions.values())) removeSession(session);
        sessions.clear();
        pairings.clear();
        credentials.clear();
        credentialByPlayer.clear();
        workers.shutdownNow();
        heartbeat.shutdownNow();
    }

    public String createPairCode(UUID uuid) {
        String code;
        do {
            code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (pairings.containsKey(code));
        pairings.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis() || e.getValue().uuid.equals(uuid));
        pairings.put(code, new Pairing(uuid, System.currentTimeMillis() + 120_000L));
        return code;
    }

    public int getConnectedClients() {
        return sessions.size();
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public String getBindHost() {
        return plugin.getConfig().getString("voice.gateway.host", "0.0.0.0");
    }

    public int getPort() {
        return plugin.getConfig().getInt("voice.gateway.port", 26467);
    }

    public void disconnect(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) removeSession(session);
    }

    private void removeSession(Session target) {
        if (target == null || target.uuid == null) return;
        UUID uuid = target.uuid;
        boolean removed = sessions.remove(uuid, target);
        if (removed) manager.setConnected(uuid, false);
        target.close();
    }

    private void heartbeat() {
        if (!running) return;
        long now = System.currentTimeMillis();
        for (Session session : new ArrayList<>(sessions.values())) {
            if (session.uuid == null) continue;
            if (now - session.lastPongAt > 15_000L) {
                removeSession(session);
                continue;
            }
            try {
                session.send(PING, session.uuid, session.heartbeatSequence++, new byte[0]);
            } catch (IOException exception) {
                removeSession(session);
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(35_000);
                workers.submit(() -> handle(socket));
            } catch (SocketException exception) {
                if (running) plugin.getLogger().warning("Voice gateway accept error: " + exception.getMessage());
            } catch (IOException exception) {
                if (running) plugin.getLogger().warning("Voice gateway accept error: " + exception.getMessage());
            }
        }
    }

    private void handle(Socket socket) {
        Session session = new Session(socket);
        try (socket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            session.input = input;
            session.output = output;
            if (!readHello(session)) return;

            while (running && !socket.isClosed()) {
                if (input.readInt() != MAGIC) return;
                byte type = input.readByte();
                UUID sender = readUuid(input);
                int sequence = input.readInt();
                int length = input.readInt();

                if (length < 0 || length > MAX_FRAME_SIZE) return;
                byte[] payload = input.readNBytes(length);
                if (payload.length != length) return;
                if (!sender.equals(session.uuid)) return;

                if (type == AUDIO) {
                    handleAudio(session, sequence, payload);
                } else if (type == PONG) {
                    session.lastPongAt = System.currentTimeMillis();
                } else if (type == GOODBYE) {
                    return;
                } else {
                    return;
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException exception) {
            if (running) plugin.getLogger().fine("Voice client disconnected: " + exception.getMessage());
        } finally {
            removeSession(session);
        }
    }

    private boolean readHello(Session session) throws IOException {
        if (session.input.readInt() != MAGIC) return false;
        if (session.input.readByte() != HELLO) return false;

        UUID presentedUuid = readUuid(session.input);
        int sequence = session.input.readInt();
        int length = session.input.readInt();
        if (sequence != 0 || length <= 0 || length > 1024) return false;

        byte[] payload = session.input.readNBytes(length);
        if (payload.length != length) return false;

        String credential = new String(payload, StandardCharsets.UTF_8);
        boolean validToken = MessageDigest.isEqual(
                credential.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));

        UUID uuid = presentedUuid;
        boolean validPair = false;
        if (credential.startsWith("PAIR:")) {
            String code = credential.substring(5);
            Pairing pairing = pairings.get(code);
            boolean uuidOmitted = presentedUuid.getMostSignificantBits() == 0L
                    && presentedUuid.getLeastSignificantBits() == 0L;
            if (pairing != null && pairing.expiresAt >= System.currentTimeMillis()
                    && (uuidOmitted || pairing.uuid.equals(presentedUuid))) {
                validPair = pairings.remove(code, pairing);
                if (validPair) uuid = pairing.uuid;
            }
        }

        boolean validSession = false;
        if (credential.startsWith("SESSION:")) {
            String sessionToken = credential.substring("SESSION:".length());
            SessionCredential stored = credentials.get(sessionToken);
            if (stored != null && stored.expiresAt >= System.currentTimeMillis()) {
                uuid = stored.uuid;
                validSession = true;
            } else if (stored != null) {
                credentials.remove(sessionToken, stored);
            }
        }

        if (!validToken && !validPair && !validSession) {
            sendHelloError(session, "AUTH");
            return false;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            sendHelloError(session, "PLAYER_OFFLINE");
            return false;
        }

        Session previous = sessions.put(uuid, session);
        if (previous != null && previous != session) {
            previous.close();
        }

        session.uuid = uuid;
        manager.setConnected(uuid, true);
        String sessionToken = findOrCreateSessionToken(uuid);
        session.send(HELLO, uuid, 0, ("OK\nSESSION:" + sessionToken).getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private void sendHelloError(Session session, String reason) {
        try {
            session.send(HELLO, new UUID(0L, 0L), 0,
                    ("ERROR:" + reason).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    private void handleAudio(Session session, int sequence, byte[] payload) {
        if (sessions.get(session.uuid) != session) return;
        if (!manager.isConnected(session.uuid) || manager.isMicMuted(session.uuid)) return;
        if (payload.length != 640) return;
        if (session.lastAudioSequence >= 0 && Integer.compareUnsigned(sequence, session.lastAudioSequence) <= 0) return;
        session.lastAudioSequence = sequence;

        VoiceRoute speaker = manager.getRoute(session.uuid);
        if (speaker == null || speaker.micMuted() || manager.isChannelMuted(speaker.channel(), speaker.uuid())) return;
        manager.markSpeaking(session.uuid);

        for (Session recipient : new ArrayList<>(sessions.values())) {
            if (recipient.uuid == null || recipient.uuid.equals(session.uuid)) continue;
            if (!manager.isConnected(recipient.uuid)) continue;

            VoiceRoute listener = manager.getRoute(recipient.uuid);
            if (listener == null || !listener.canHear(speaker)) continue;

            try {
                double volume = manager.getVolume(recipient.uuid);
                byte[] audio = applyVolume(payload, volume);
                recipient.send(AUDIO, session.uuid, sequence, audio);
            } catch (IOException exception) {
                removeSession(recipient);
            }
        }
    }

    private static byte[] applyVolume(byte[] pcm, double volume) {
        if (volume == 1.0 || pcm.length < 2) return pcm;
        byte[] adjusted = pcm.clone();
        double gain = Math.max(0.0, Math.min(2.0, volume));
        for (int i = 0; i + 1 < adjusted.length; i += 2) {
            int sample = (short) ((adjusted[i] & 0xFF) | (adjusted[i + 1] << 8));
            int scaled = (int) Math.round(sample * gain);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            adjusted[i] = (byte) scaled;
            adjusted[i + 1] = (byte) (scaled >> 8);
        }
        return adjusted;
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private synchronized String findOrCreateSessionToken(UUID uuid) {
        long now = System.currentTimeMillis();
        credentials.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt < now;
            if (expired) credentialByPlayer.remove(e.getValue().uuid, e.getKey());
            return expired;
        });

        String existing = credentialByPlayer.get(uuid);
        if (existing != null) {
            SessionCredential stored = credentials.get(existing);
            if (stored != null && stored.uuid.equals(uuid)) {
                stored.expiresAt = now + 86_400_000L;
                return existing;
            }
            credentialByPlayer.remove(uuid, existing);
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        credentials.put(value, new SessionCredential(uuid, now + 86_400_000L));
        credentialByPlayer.put(uuid, value);
        return value;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class SessionCredential {
        private final UUID uuid;
        private volatile long expiresAt;
        private SessionCredential(UUID uuid, long expiresAt) {
            this.uuid = uuid;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Pairing {
        private final UUID uuid;
        private final long expiresAt;
        private Pairing(UUID uuid, long expiresAt) { this.uuid = uuid; this.expiresAt = expiresAt; }
    }

    private static final class Session {
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private UUID uuid;
        private volatile long lastPongAt = System.currentTimeMillis();
        private int heartbeatSequence;
        private int lastAudioSequence = -1;

        private Session(Socket socket) {
            this.socket = socket;
        }

        private synchronized void send(byte type, UUID sender, int sequence, byte[] payload) throws IOException {
            if (output == null) throw new IOException("Voice session is not ready.");
            output.writeInt(MAGIC);
            output.writeByte(type);
            output.writeLong(sender.getMostSignificantBits());
            output.writeLong(sender.getLeastSignificantBits());
            output.writeInt(sequence);
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
        }

        private void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}