package pipeline;

import command.CommandResponse;
import protocol.ProtocolSender;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ResponseEncriptor implements Encriptor {

    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();
    private final ProtocolSender proto = new ProtocolSender();

    @Override
    public byte[] encrypt(CommandResponse response) {
        try {
            byte[] body = encodeBody(response);
            int cType = response.success ? 0 : -1;
            return proto.sendPacket((byte) 0x02, response.packetId, cType, response.userId, body, AES_KEY);
        } catch (Exception e) {
            System.err.println("[Encriptor] " + e.getMessage());
            return new byte[0];
        }
    }

    private byte[] encodeBody(CommandResponse r) {
        byte[] msgBytes = r.message.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(5 + msgBytes.length)
                .put((byte) (r.success ? 1 : 0))
                .putInt(r.intData)
                .put(msgBytes)
                .array();
    }
}
