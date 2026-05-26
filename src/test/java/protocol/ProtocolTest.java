package protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();

    private byte[] buildPacket(byte bSrc, long bPktId, int cType, int bUserId,
                               byte[] body, byte[] key) throws Exception {
        return new ProtocolSender().sendPacket(bSrc, bPktId, cType, bUserId, body, key);
    }

    @Test
    void receivePacket_parsesAllFieldsCorrectly() throws Exception {
        byte[] body = "hello world".getBytes();
        byte[] raw  = buildPacket((byte) 0x01, 42L, 100, 200, body, null);

        ProtocolPayload payload = new ProtocolParser().receivePacket(raw);

        assertEquals(0x13, payload.bMagic & 0xFF);
        assertEquals(0x01, payload.bSrc  & 0xFF);
        assertEquals(42L, payload.bPktId);
        assertEquals(100, payload.message.cType);
        assertEquals(200, payload.message.bUserId);
        assertArrayEquals(body, payload.message.message);
    }

    @Test
    void receivePacket_invalidMagicByte_throws() {
        byte[] raw = new byte[20];
        raw[0] = 0x00;
        assertThrows(IllegalArgumentException.class, () -> new ProtocolParser().receivePacket(raw));
    }

    @Test
    void receivePacket_tooShort_throws() {
        byte[] raw = new byte[10];
        assertThrows(IllegalArgumentException.class, () -> new ProtocolParser().receivePacket(raw));
    }

    @Test
    void receivePacket_headerCrcMismatch_throws() throws Exception {
        byte[] raw = buildPacket((byte) 0x01, 1L, 1, 1, new byte[0], null);
        raw[14] ^= 0xFF;
        assertThrows(IllegalArgumentException.class, () -> new ProtocolParser().receivePacket(raw));
    }

    @Test
    void receivePacket_messageCrcMismatch_throws() throws Exception {
        byte[] raw = buildPacket((byte) 0x01, 1L, 1, 1, new byte[0], null);
        raw[raw.length - 1] ^= 0xFF;
        assertThrows(IllegalArgumentException.class, () -> new ProtocolParser().receivePacket(raw));
    }

    @Test
    void receivePacket_emptyBody_parsesCorrectly() throws Exception {
        byte[] raw = buildPacket((byte) 0x01, 0L, 1, 1, new byte[0], null);
        assertEquals(0, new ProtocolParser().receivePacket(raw).message.message.length);
    }

    @Test
    void receivePacket_wLen_matchesBodySize() throws Exception {
        byte[] body = new byte[20];
        byte[] raw  = buildPacket((byte) 0x01, 1L, 1, 1, body, null);
        assertEquals(28, new ProtocolParser().receivePacket(raw).wLen);
    }

    @Test
    void encryptDecrypt_roundTrip() throws Exception {
        byte[] original = "secret payload".getBytes();
        ProtocolMessage msg = new ProtocolMessage();
        msg.message = original;

        byte[] encrypted = msg.encrypt(AES_KEY);
        assertFalse(java.util.Arrays.equals(original, encrypted));

        ProtocolMessage msg2 = new ProtocolMessage();
        msg2.message = encrypted;
        assertArrayEquals(original, msg2.decrypt(AES_KEY));
    }

    @Test
    void sendReceive_withEncryption_roundTrip() throws Exception {
        byte[] body = "secret data".getBytes();
        byte[] raw  = buildPacket((byte) 0x02, 99L, 5, 10, body, AES_KEY);

        ProtocolPayload payload = new ProtocolParser().receivePacket(raw);
        assertArrayEquals(body, payload.message.decrypt(AES_KEY));
    }

    @Test
    void sendReceive_withoutEncryption_roundTrip() throws Exception {
        byte[] body = "plain data".getBytes();
        byte[] raw  = buildPacket((byte) 0x03, 7L, 3, 4, body, null);
        assertArrayEquals(body, new ProtocolParser().receivePacket(raw).message.message);
    }
}
