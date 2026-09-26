package dev.truc5738.voiceclient;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class VoiceProtocolIntegrationTest {
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1, AUDIO = 2, GOODBYE = 3, PING = 4, PONG = 5;
    private static final int FRAME_BYTES = 640;

    @Test
    void tcpHandshakePingAudioAndGoodbyeRoundTrip() throws Exception {
        UUID player = UUID.randomUUID();
        UUID speaker = UUID.randomUUID();
        byte[] audio = new byte[FRAME_BYTES];
        for (int i = 0; i < audio.length; i++) audio[i] = (byte) (i * 3);

        try (ServerSocket server = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> serverTask = executor.submit(() -> {
                try (Socket socket = server.accept();
                     DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                     DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

                    Frame hello = readFrame(in);
                    assertEquals(MAGIC, hello.magic);
                    assertEquals(HELLO, hello.type);
                    assertEquals(0, hello.sequence);
                    assertEquals("PAIR:123456", new String(hello.payload, StandardCharsets.UTF_8));

                    writeFrame(out, HELLO, player, 0,
                            "OK\nSESSION:test-session".getBytes(StandardCharsets.UTF_8));

                    writeFrame(out, PING, player, 7, new byte[0]);
                    Frame pong = readFrame(in);
                    assertEquals(PONG, pong.type);
                    assertEquals(player, pong.uuid);
                    assertEquals(7, pong.sequence);
                    assertEquals(0, pong.payload.length);

                    writeFrame(out, AUDIO, speaker, 42, audio);
                    Frame goodbye = readFrame(in);
                    assertEquals(GOODBYE, goodbye.type);
                    assertEquals(player, goodbye.uuid);
                    assertEquals(0, goodbye.payload.length);
                }
                return null;
            });

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort());
                 DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()))) {

                writeFrame(out, HELLO, new UUID(0L, 0L), 0,
                        "PAIR:123456".getBytes(StandardCharsets.UTF_8));

                Frame ack = readFrame(in);
                assertEquals(HELLO, ack.type);
                assertEquals(player, ack.uuid);
                assertEquals("OK\nSESSION:test-session",
                        new String(ack.payload, StandardCharsets.UTF_8));

                Frame ping = readFrame(in);
                assertEquals(PING, ping.type);
                assertEquals(7, ping.sequence);
                writeFrame(out, PONG, player, ping.sequence, new byte[0]);

                Frame receivedAudio = readFrame(in);
                assertEquals(AUDIO, receivedAudio.type);
                assertEquals(speaker, receivedAudio.uuid);
                assertEquals(42, receivedAudio.sequence);
                assertArrayEquals(audio, receivedAudio.payload);

                writeFrame(out, GOODBYE, player, 0, new byte[0]);
            }

            serverTask.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void malformedLengthIsRejectedByReaderContract() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", server.getLocalPort());
             Socket accepted = server.accept();
             DataOutputStream out = new DataOutputStream(accepted.getOutputStream());
             DataInputStream in = new DataInputStream(client.getInputStream())) {

            out.writeInt(MAGIC);
            out.writeByte(AUDIO);
            out.writeLong(0L);
            out.writeLong(0L);
            out.writeInt(1);
            out.writeInt(17 * 1024);
            out.flush();

            assertEquals(MAGIC, in.readInt());
            assertEquals(AUDIO, in.readByte());
            in.readLong();
            in.readLong();
            assertEquals(1, in.readInt());
            assertTrue(in.readInt() > 16 * 1024);
        }
    }

    private static Frame readFrame(DataInputStream in) throws IOException {
        int magic = in.readInt();
        byte type = in.readByte();
        UUID uuid = new UUID(in.readLong(), in.readLong());
        int sequence = in.readInt();
        int length = in.readInt();
        if (length < 0 || length > 16 * 1024) {
            throw new IOException("Invalid frame length: " + length);
        }
        byte[] payload = in.readNBytes(length);
        if (payload.length != length) throw new EOFException("Truncated frame");
        return new Frame(magic, type, uuid, sequence, payload);
    }

    private static void writeFrame(DataOutputStream out, byte type, UUID uuid,
                                   int sequence, byte[] payload) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(type);
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeInt(sequence);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private record Frame(int magic, byte type, UUID uuid, int sequence, byte[] payload) {}
}
