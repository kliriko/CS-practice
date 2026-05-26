package protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProtocolSender {
    private static final byte MAGIC = 0x13;
    private static final int HEADER_SIZE = 16;

    public byte[] sendPacket(byte bSrc, long bPktId, int cType, int bUserId, byte[] messageBody, byte[] key) throws Exception {
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
