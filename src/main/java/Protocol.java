import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

class ProtocolPayload {
    byte bMagic;
    byte bSrc;
    long bPktId;
    int wLen;
    int wCrc16Header;
    int wCrc16Message;
    ProtocolMessage message;
}

class ProtocolMessage {
    int cType;
    int bUserId;
    byte[] message;

    byte[] decrypt(byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(message);
    }

    byte[] encrypt(byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(message);
    }
}

class ProtocolParser {
    private static final byte MAGIC = 0x13;
    private static final int HEADER_SIZE = 16;

    ProtocolPayload receivePacket(byte[] rawData) {
        if (rawData.length < HEADER_SIZE + 2) {
            throw new IllegalArgumentException("Packet too short");
        }

        ProtocolPayload payload = new ProtocolPayload();
        ByteBuffer buffer = ByteBuffer.wrap(rawData).order(ByteOrder.BIG_ENDIAN);

        payload.bMagic = buffer.get(0);
        if (payload.bMagic != MAGIC) {
            throw new IllegalArgumentException("Invalid magic byte");
        }

        payload.bSrc = buffer.get(1);
        payload.bPktId = buffer.getLong(2);
        payload.wLen = buffer.getInt(10);
        payload.wCrc16Header = buffer.getShort(14) & 0xFFFF;

        if (rawData.length < HEADER_SIZE + payload.wLen + 2) {
            throw new IllegalArgumentException("Packet too short for declared wLen");
        }

        int computedHeaderCrc = Crc16.calculateCrc(Arrays.copyOfRange(rawData, 0, 14)) & 0xFFFF;
        if (computedHeaderCrc != payload.wCrc16Header) {
            throw new IllegalArgumentException("Header CRC16 mismatch");
        }

        payload.wCrc16Message = buffer.getShort(HEADER_SIZE + payload.wLen) & 0xFFFF;
        int computedMsgCrc = Crc16.calculateCrc(Arrays.copyOfRange(rawData, HEADER_SIZE, HEADER_SIZE + payload.wLen)) & 0xFFFF;
        if (computedMsgCrc != payload.wCrc16Message) {
            throw new IllegalArgumentException("Message CRC16 mismatch");
        }

        if (payload.wLen < 8) {
            throw new IllegalArgumentException("Message too short to contain cType and bUserId");
        }

        ProtocolMessage msg = new ProtocolMessage();
        msg.cType = buffer.getInt(HEADER_SIZE);
        msg.bUserId = buffer.getInt(HEADER_SIZE + 4);
        msg.message = Arrays.copyOfRange(rawData, HEADER_SIZE + 8, HEADER_SIZE + payload.wLen);
        payload.message = msg;

        return payload;
    }
}

class ProtocolSender {
    private static final byte MAGIC = 0x13;
    private static final int HEADER_SIZE = 16;

    byte[] sendPacket(byte bSrc, long bPktId, int cType, int bUserId, byte[] messageBody, byte[] key) throws Exception {
        ProtocolMessage msg = new ProtocolMessage();
        msg.message = messageBody;
        byte[] encryptedBody = key != null ? msg.encrypt(key) : messageBody;

        int wLen = 8 + encryptedBody.length;

        byte[] msgBytes = new byte[wLen];
        ByteBuffer msgBuffer = ByteBuffer.wrap(msgBytes).order(ByteOrder.BIG_ENDIAN);
        msgBuffer.putInt(cType);
        msgBuffer.putInt(bUserId);
        msgBuffer.put(encryptedBody);

        byte[] headerBytes = new byte[14];
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        headerBuffer.put(MAGIC);
        headerBuffer.put(bSrc);
        headerBuffer.putLong(bPktId);
        headerBuffer.putInt(wLen);

        int headerCrc = Crc16.calculateCrc(headerBytes) & 0xFFFF;
        int msgCrc = Crc16.calculateCrc(msgBytes) & 0xFFFF;

        byte[] packet = new byte[HEADER_SIZE + wLen + 2];
        ByteBuffer packetBuffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);
        packetBuffer.put(headerBytes);
        packetBuffer.putShort((short) headerCrc);
        packetBuffer.put(msgBytes);
        packetBuffer.putShort((short) msgCrc);

        return packet;
    }
}