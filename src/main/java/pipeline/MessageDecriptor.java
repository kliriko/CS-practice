package pipeline;

import command.CommandCodec;
import command.WarehouseCommand;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;

import java.util.concurrent.BlockingQueue;

public class MessageDecriptor implements Decriptor {
    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();

    private final BlockingQueue<WarehouseCommand> outputQueue;
    private final ProtocolParser parser = new ProtocolParser();

    public MessageDecriptor(BlockingQueue<WarehouseCommand> outputQueue) {
        this.outputQueue = outputQueue;
    }

    @Override
    public void decrypt(byte[] message) {
        try {
            ProtocolPayload payload = parser.receivePacket(message);
            payload.message.message = payload.message.decrypt(AES_KEY);
            WarehouseCommand cmd = CommandCodec.decode(payload);
            outputQueue.put(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[Decriptor] " + e.getMessage());
        }
    }
}
