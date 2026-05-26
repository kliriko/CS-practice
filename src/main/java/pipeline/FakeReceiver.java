package pipeline;

import command.CommandCodec;
import command.WarehouseCommand;
import protocol.ProtocolSender;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FakeReceiver implements Receiver {
    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();
    private static final String[] PRODUCT_NAMES = {"buckwheat", "rice", "flour", "sugar", "salt"};
    private static final String[] GROUP_NAMES = {"cereals", "dairy", "vegetables"};

    private final int messageCount;
    private final long delayMs;
    private final BlockingQueue<byte[]> outputQueue;
    private final AtomicInteger sent = new AtomicInteger(0);
    private final AtomicLong pktId = new AtomicLong(1);
    private final Random rng = new Random();
    private final ProtocolSender proto = new ProtocolSender();

    public FakeReceiver(int messageCount, long delayMs, BlockingQueue<byte[]> outputQueue) {
        this.messageCount = messageCount;
        this.delayMs = delayMs;
        this.outputQueue = outputQueue;
    }

    @Override
    public void receiveMessage() {
        if (messageCount >= 0 && sent.get() >= messageCount) {
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        try {
            if (delayMs > 0) Thread.sleep(delayMs);
            outputQueue.put(generatePacket());
            sent.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[FakeReceiver] " + e.getMessage());
        }
    }

    private byte[] generatePacket() throws Exception {
        int type = rng.nextInt(6) + 1;
        byte[] body = switch (type) {
            case 1 -> CommandCodec.encodeGetStock(rng.nextInt(5) + 1);
            case 2 -> CommandCodec.encodeDebitStock(rng.nextInt(5) + 1, rng.nextInt(10) + 1);
            case 3 -> CommandCodec.encodeCreditStock(rng.nextInt(5) + 1, rng.nextInt(50) + 1);
            case 4 -> CommandCodec.encodeAddGroup(GROUP_NAMES[rng.nextInt(GROUP_NAMES.length)]);
            case 5 -> CommandCodec.encodeAddProduct(rng.nextInt(3) + 1, PRODUCT_NAMES[rng.nextInt(PRODUCT_NAMES.length)]);
            default -> CommandCodec.encodeSetPrice(rng.nextInt(5) + 1, rng.nextDouble() * 100);
        };
        return proto.sendPacket((byte) 0x01, pktId.getAndIncrement(), type, 1, body, AES_KEY);
    }

    public boolean isDone() {
        return messageCount >= 0 && sent.get() >= messageCount;
    }
}
