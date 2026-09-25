package dev.truc5738.voiceclient;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class VoiceClient {
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1, AUDIO = 2, GOODBYE = 3;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    private static final int FRAME_BYTES = SAMPLES * 2;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: java -jar voice-client.jar <host> <port> <uuid> <token>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        UUID uuid = UUID.fromString(args[2]);
        String token = args[3];

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (Socket socket = new Socket(host, port);
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
             SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format))) {

            socket.setTcpNoDelay(true);
            send(out, HELLO, uuid, 0, token.getBytes(StandardCharsets.UTF_8));
            mic.open(format);
            speaker.open(format);
            mic.start();
            speaker.start();

            Thread receiver = new Thread(() -> receive(in, uuid, speaker), "VoiceClient-Receiver");
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

    private static void receive(DataInputStream in, UUID self, SourceDataLine speaker) {
        try {
            while (true) {
                if (in.readInt() != MAGIC) return;
                byte type = in.readByte();
                UUID sender = new UUID(in.readLong(), in.readLong());
                in.readInt();
                int length = in.readInt();
                if (length < 0 || length > 16384) return;
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) return;
                if (type == AUDIO && !sender.equals(self)) speaker.write(payload, 0, payload.length);
            }
        } catch (IOException ignored) {
        }
    }
}