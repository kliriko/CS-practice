package pipeline;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeSender implements Sender {

    private final AtomicInteger sentCount = new AtomicInteger(0);

    @Override
    public void sendMessage(byte[] message, InetAddress target) {
        sentCount.incrementAndGet();
        System.out.printf("[FakeSender] → %d bytes%n", message.length);
    }

    public int getSentCount() {
        return sentCount.get();
    }
}
