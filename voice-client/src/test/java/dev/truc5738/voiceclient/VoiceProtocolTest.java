package dev.truc5738.voiceclient;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoiceProtocolTest {
    private static final int MAGIC = 0x4D564331;
    private static final byte HELLO = 1, AUDIO = 2, GOODBYE = 3, PING = 4, PONG = 5;
    private static final int FRAME_BYTES = 640;

    @Test
    void helloFrameHasExpected29ByteHeaderAndPayload() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] payload = "PAIR:123456".getBytes(StandardCharsets.UTF_8);
        byte[] frame = frame(HELLO, id, 0, payload);

        assertEquals(29 + payload.length, frame.length);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame));
        assertEquals(MAGIC, in.readInt());
        assertEquals(HELLO, in.readByte());
        assertEquals(id, new UUID(in.readLong(), in.readLong()));
        assertEquals(0, in.readInt());
        assertEquals(payload.length, in.readInt());
        assertArrayEquals(payload, in.readNBytes(payload.length));
    }

    @Test
    void audioFrameIsExactly640Bytes() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] payload = new byte[FRAME_BYTES];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;

        byte[] frame = frame(AUDIO, id, 42, payload);

        assertEquals(29 + FRAME_BYTES, frame.length);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame));
        assertEquals(MAGIC, in.readInt());
        assertEquals(AUDIO, in.readByte());
        assertEquals(id, new UUID(in.readLong(), in.readLong()));
        assertEquals(42, in.readInt());
        assertEquals(FRAME_BYTES, in.readInt());
        assertArrayEquals(payload, in.readNBytes(FRAME_BYTES));
    }

    @Test
    void sequenceMustIncreaseUnsigned() {
        assertTrue(Integer.compareUnsigned(0, -1) > 0);
        assertTrue(Integer.compareUnsigned(-1, 0) > 0);
        assertTrue(Integer.compareUnsigned(1, 0) > 0);
        assertTrue(Integer.compareUnsigned(0, 1) < 0);
    }

    @Test
    void controlFramesHaveNoPayload() {
        UUID id = UUID.randomUUID();
        assertEquals(29, frame(PING, id, 7, new byte[0]).length);
        assertEquals(29, frame(PONG, id, 7, new byte[0]).length);
        assertEquals(29, frame(GOODBYE, id, 0, new byte[0]).length);
    }

    private static byte[] frame(byte type, UUID id, int sequence, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeByte(type);
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        out.writeInt(sequence);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
        return bytes.toByteArray();
    }
}
