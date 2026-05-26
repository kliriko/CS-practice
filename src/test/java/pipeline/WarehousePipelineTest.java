package pipeline;

import command.CommandCodec;
import command.WarehouseCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;
import protocol.ProtocolSender;
import warehouse.Warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WarehousePipelineTest {

    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();

    // ── Warehouse unit tests ──────────────────────────────────────────────────

    @Test
    void warehouse_addGroupAndProduct_idsIncrement() {
        Warehouse wh = new Warehouse();
        assertEquals(1, wh.addGroup("cereals"));
        assertEquals(1, wh.addProduct(1, "buckwheat"));
        assertEquals(2, wh.addProduct(1, "rice"));
    }

    @Test
    void warehouse_creditAndGetStock() {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("g");
        int p = wh.addProduct(g, "rice");
        wh.creditStock(p, 100);
        assertEquals(100, wh.getStock(p));
    }

    @Test
    void warehouse_debitStock_reducesQuantity() {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("g");
        int p = wh.addProduct(g, "rice");
        wh.creditStock(p, 50);
        wh.debitStock(p, 20);
        assertEquals(30, wh.getStock(p));
    }

    @Test
    void warehouse_debitStock_insufficientThrows() {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("g");
        int p = wh.addProduct(g, "rice");
        wh.creditStock(p, 5);
        assertThrows(IllegalStateException.class, () -> wh.debitStock(p, 10));
    }

    @Test
    void warehouse_setPrice_persists() {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("g");
        int p = wh.addProduct(g, "salt");
        wh.setPrice(p, 49.99);
        assertEquals(49.99, wh.getPrice(p), 0.001);
    }

    // ── Concurrent Warehouse tests ────────────────────────────────────────────

    @Test
    @Timeout(10)
    void warehouse_concurrentCredit_noRaceCondition() throws InterruptedException {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("grains");
        int p = wh.addProduct(g, "buckwheat");

        int threads = 20, creditsEach = 10;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await();
                    wh.creditStock(p, creditsEach);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(threads * creditsEach, wh.getStock(p));
    }

    @Test
    @Timeout(10)
    void warehouse_concurrentDebitCredit_stockNeverNegative() throws InterruptedException {
        Warehouse wh = new Warehouse();
        int g = wh.addGroup("g");
        int p = wh.addProduct(g, "sugar");
        wh.creditStock(p, 10_000);

        int threads = 10;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            boolean credit = (i % 2 == 0);
            pool.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 100; j++) {
                        if (credit) wh.creditStock(p, 2);
                        else        wh.debitStock(p, 2);
                    }
                } catch (IllegalStateException e) {
                    errors.incrementAndGet();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(wh.getStock(p) >= 0, "Stock must never go negative");
    }

    // ── CommandCodec round-trip tests ─────────────────────────────────────────

    @Test
    void codec_creditStock_roundTrip() throws Exception {
        byte[] body = CommandCodec.encodeCreditStock(7, 42);
        byte[] raw  = new ProtocolSender().sendPacket((byte) 0x01, 1L,
                          WarehouseCommand.Type.CREDIT_STOCK.code, 1, body, AES_KEY);

        ProtocolPayload payload = new ProtocolParser().receivePacket(raw);
        payload.message.message = payload.message.decrypt(AES_KEY);
        WarehouseCommand cmd = CommandCodec.decode(payload);

        assertInstanceOf(WarehouseCommand.CreditStock.class, cmd);
        assertEquals(7,  ((WarehouseCommand.CreditStock) cmd).productId);
        assertEquals(42, ((WarehouseCommand.CreditStock) cmd).quantity);
    }

    @Test
    void codec_addGroup_roundTrip() throws Exception {
        byte[] body = CommandCodec.encodeAddGroup("vegetables");
        byte[] raw  = new ProtocolSender().sendPacket((byte) 0x01, 1L,
                          WarehouseCommand.Type.ADD_GROUP.code, 1, body, AES_KEY);

        ProtocolPayload payload = new ProtocolParser().receivePacket(raw);
        payload.message.message = payload.message.decrypt(AES_KEY);
        WarehouseCommand cmd = CommandCodec.decode(payload);

        assertInstanceOf(WarehouseCommand.AddGroup.class, cmd);
        assertEquals("vegetables", ((WarehouseCommand.AddGroup) cmd).groupName);
    }

    @Test
    void codec_setPrice_roundTrip() throws Exception {
        byte[] body = CommandCodec.encodeSetPrice(3, 99.95);
        byte[] raw  = new ProtocolSender().sendPacket((byte) 0x01, 1L,
                          WarehouseCommand.Type.SET_PRICE.code, 1, body, AES_KEY);

        ProtocolPayload payload = new ProtocolParser().receivePacket(raw);
        payload.message.message = payload.message.decrypt(AES_KEY);
        WarehouseCommand cmd = CommandCodec.decode(payload);

        assertInstanceOf(WarehouseCommand.SetPrice.class, cmd);
        assertEquals(99.95, ((WarehouseCommand.SetPrice) cmd).price, 0.001);
    }

    // ── Pipeline integration tests ────────────────────────────────────────────

    @Test
    @Timeout(15)
    void pipeline_fakeReceiver_allResponsesDelivered() throws Exception {
        // Share the same queue between FakeReceiver and the pipeline
        LinkedBlockingQueue<byte[]> sharedRaw = new LinkedBlockingQueue<>();
        FakeReceiver fakeReceiver = new FakeReceiver(20, 0, sharedRaw);
        FakeSender   fakeSender   = new FakeSender();
        WarehousePipeline pipeline = new WarehousePipeline(fakeReceiver, sharedRaw, fakeSender);

        // Pre-seed warehouse so random commands don't all error
        Warehouse wh = pipeline.getWarehouse();
        for (int i = 0; i < 3; i++) wh.addGroup("group" + i);
        for (int g = 1; g <= 3; g++)
            for (int j = 0; j < 2; j++) {
                int pid = wh.addProduct(g, "product" + j);
                wh.creditStock(pid, 500);
            }

        pipeline.start();

        long deadline = System.currentTimeMillis() + 10_000;
        while (!fakeReceiver.isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(500); // let queues drain
        pipeline.stop();
        pipeline.awaitTermination(3_000);

        assertTrue(fakeSender.getSentCount() > 0, "Expected responses to be sent");
    }

    @Test
    @Timeout(15)
    void pipeline_concurrentSenders_exactCount() throws Exception {
        FakeSender fakeSender = new FakeSender();

        // Receiver that never generates on its own (messageCount = 0)
        FakeReceiver idleReceiver = new FakeReceiver(0, 0, new LinkedBlockingQueue<>());
        WarehousePipeline pipeline = new WarehousePipeline(idleReceiver, fakeSender);

        Warehouse wh = pipeline.getWarehouse();
        int gId = wh.addGroup("grains");
        int pId = wh.addProduct(gId, "buckwheat");
        wh.creditStock(pId, 100_000);

        pipeline.start();

        int senderCount = 10, msgsEach = 30;
        ExecutorService pool = Executors.newFixedThreadPool(senderCount);
        CyclicBarrier barrier = new CyclicBarrier(senderCount);
        ProtocolSender proto = new ProtocolSender();
        LinkedBlockingQueue<byte[]> rawQueue = pipeline.getRawQueue();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < senderCount; i++) {
            final long base = i * 1000L;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < msgsEach; j++) {
                        byte[] body   = CommandCodec.encodeCreditStock(pId, 1);
                        byte[] packet = proto.sendPacket((byte) 0x01, base + j,
                                WarehouseCommand.Type.CREDIT_STOCK.code, 1, body, AES_KEY);
                        rawQueue.put(packet);
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
                return null;
            }));
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        for (Future<?> f : futures) f.get(); // rethrow any exception

        Thread.sleep(1_000); // drain pipeline
        pipeline.stop();
        pipeline.awaitTermination(3_000);

        int expected = senderCount * msgsEach;
        assertEquals(expected, fakeSender.getSentCount());
        assertEquals(100_000 + expected, wh.getStock(pId));
    }

    @Test
    @Timeout(15)
    void pipeline_stockNeverNegative_underConcurrentLoad() throws Exception {
        FakeSender fakeSender = new FakeSender();
        FakeReceiver idleReceiver = new FakeReceiver(0, 0, new LinkedBlockingQueue<>());
        WarehousePipeline pipeline = new WarehousePipeline(idleReceiver, fakeSender);

        Warehouse wh = pipeline.getWarehouse();
        int gId = wh.addGroup("g");
        int pId = wh.addProduct(gId, "rice");
        wh.creditStock(pId, 10_000);

        pipeline.start();

        int threads = 8, msgsEach = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ProtocolSender proto = new ProtocolSender();

        for (int i = 0; i < threads; i++) {
            boolean credit = (i % 2 == 0);
            final long base = i * 1000L;
            pool.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < msgsEach; j++) {
                        byte[] body = credit
                                ? CommandCodec.encodeCreditStock(pId, 5)
                                : CommandCodec.encodeDebitStock(pId, 5);
                        int cType = credit
                                ? WarehouseCommand.Type.CREDIT_STOCK.code
                                : WarehouseCommand.Type.DEBIT_STOCK.code;
                        pipeline.getRawQueue().put(
                                proto.sendPacket((byte) 0x01, base + j, cType, 1, body, AES_KEY));
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }

        pool.shutdown();
        pool.awaitTermination(8, TimeUnit.SECONDS);
        Thread.sleep(1_000);
        pipeline.stop();
        pipeline.awaitTermination(3_000);

        assertTrue(wh.getStock(pId) >= 0, "Stock must never go negative");
    }
}
