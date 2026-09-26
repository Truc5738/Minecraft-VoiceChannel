package dev.truc5738.voiceclient;

import javax.sound.sampled.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VoiceClient {
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1, AUDIO = 2, GOODBYE = 3, PING = 4, PONG = 5;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    private static final int FRAME_BYTES = SAMPLES * 2;
    private static final int MAX_FRAME_SIZE = 16 * 1024;

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
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(20000);

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
                 SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format))) {
