package dev.truc5738.voiceclient;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class VoiceClient {
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1, AUDIO = 2, GOODBYE = 3, PING = 4, PONG = 5;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    private static final int FRAME_BYTES = SAMPLES * 2;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage:");
            System.out.println("  java -jar voice-client.jar <host> <port> <uuid> <token>");
            System.out.println("  java -jar voice-client.jar <host> <port> pair <6-digit-code>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        UUID uuid;
        String token;
        if (args[2].equalsIgnoreCase("pair")) {
            uuid = new UUID(0L, 0L);
            token = "PAIR:" + args[3];
        } else {
            uuid = UUID.fromString(args[2]);
            token = args[3];
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (Socket socket = new Socket(host, port);
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
             SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format))) {

            socket.setTcpNoDelay(true);
            send(out, HELLO, uuid, 0, token.getBytes(StandardCharsets.UTF_8));
            UUID assignedUuid = readHelloAck(in, uuid);
            if (assignedUuid == null) {
                throw new IOException("Voice gateway authentication rejected.");
            }
            uuid = assignedUuid;
            mic.open(format);
            speaker.open(format);
            mic.start();
            speaker.start();

            Thread receiver = new Thread(() -> receive(in, out, uuid, speaker), "VoiceClient-Receiver");
            receiver.setDaemon(true);
            receiver.start();

            System.out.println("Connected. Microphone is active. Press Enter to stop.");
            Thread capture = new Thread(() -> {
                byte[] frame = new byte[FRAME_BYTES];
                int sequence = 0;
                try {
                    while (!socket.isClosed()) {
                        int offset = 0;
                        while (offset < frame.length) {
                            int n = mic.read(frame, offset, frame.length - offset);
                            if (n <= 0) break;
                            offset += n;
                        }
                        if (offset == frame.length) {
                            send(out, AUDIO, uuid, sequence++, frame);
                        }
                    }
                } catch (IOException ignored) {
                }
            }, "VoiceClient-Capture");
            capture.setDaemon(true);
            capture.start();

            System.in.read();
            send(out, GOODBYE, uuid, 0, new byte[0]);
        }
    }

    private static synchronized void send(DataOutputStream out, byte type, UUID uuid, int sequence, byte[] payload) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(type);
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeInt(sequence);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private static UUID readHelloAck(DataInputStream in, UUID self) throws IOException {
        if (in.readInt() != MAGIC) return null;
        if (in.readByte() != HELLO) return null;
        UUID assigned = new UUID(in.readLong(), in.readLong());
        in.readInt();
        int length = in.readInt();
        if (length <= 0 || length > 1024) return null;
        byte[] payload = in.readNBytes(length);
        if (payload.length != length || !new String(payload, StandardCharsets.UTF_8).startsWith("OK")) return null;
        if (self.getMostSignificantBits() != 0L && !assigned.equals(self)) return null;
        return assigned;
    }

    private static void receive(DataInputStream in, DataOutputStream out, UUID self, SourceDataLine speaker) {
        try {
            while (true) {
                if (in.readInt() != MAGIC) return;
                byte type = in.readByte();
                UUID sender = new UUID(in.readLong(), in.readLong());
                int sequence = in.readInt();
                int length = in.readInt();
                if (length < 0 || length > 16384) return;
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) return;
                if (type == AUDIO && !sender.equals(self)) {
                    speaker.write(payload, 0, payload.length);
                } else if (type == PING) {
                    send(out, PONG, self, sequence, new byte[0]);
                }
            }
        } catch (IOException ignored) {
        }
    }
}