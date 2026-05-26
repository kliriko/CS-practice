package protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ProtocolParser {
    private static final byte MAGIC = 0x13;
    private static final int HEADER_SIZE = 16;

    public ProtocolPayload receivePacket(byte[] rawData) {
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
