package pipeline;

import command.CommandResponse;
import command.WarehouseCommand;
import warehouse.Warehouse;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wires Receiver → Decriptor → Processor → Encriptor → Sender,
 * each running in its own dedicated thread.
 *
 * Shutdown is chained: stopping the receiver thread causes each
 * downstream thread to drain its input queue and exit on its own.
 */
public class WarehousePipeline {

    private static final long POLL_MS = 50;

    // Inter-stage queues
    private final LinkedBlockingQueue<byte[]>           rawQueue;
    private final LinkedBlockingQueue<WarehouseCommand> commandQueue  = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<CommandResponse>  responseQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<byte[]>           outQueue      = new LinkedBlockingQueue<>();

    // Pipeline components
    private final Receiver  receiver;
    private final Decriptor decriptor;
    private final Processor processor;
    private final Encriptor encriptor;
    private final Sender    sender;
    private final Warehouse warehouse;

    // Thread handles
    private Thread receiverThread;
    private Thread decriptorThread;
    private Thread processorThread;
    private Thread encriptorThread;
    private Thread senderThread;

    // Chained "upstream done" flags
    private final AtomicBoolean receiverDone  = new AtomicBoolean(false);
    private final AtomicBoolean decriptorDone = new AtomicBoolean(false);
    private final AtomicBoolean processorDone = new AtomicBoolean(false);
    private final AtomicBoolean encriptorDone = new AtomicBoolean(false);

    /** Uses an internal raw queue (for tests that inject packets via {@link #getRawQueue()}). */
    public WarehousePipeline(Receiver receiver, Sender sender) {
        this(receiver, new LinkedBlockingQueue<>(), sender);
    }

    /** Accepts a shared raw queue so a {@link FakeReceiver} and the pipeline use the same queue. */
    public WarehousePipeline(Receiver receiver, LinkedBlockingQueue<byte[]> rawQueue, Sender sender) {
        this.rawQueue   = rawQueue;
        this.warehouse  = new Warehouse();
        this.receiver   = receiver;
        this.decriptor  = new MessageDecriptor(commandQueue);
        this.processor  = new CommandProcessor(responseQueue, warehouse);
        this.encriptor  = new ResponseEncriptor();
        this.sender     = sender;
    }

    public Warehouse getWarehouse() { return warehouse; }

    /** Exposed for tests that inject packets directly. */
    public LinkedBlockingQueue<byte[]> getRawQueue() { return rawQueue; }

    public void start() {
        // Thread 1 – Receiver
        receiverThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiver.receiveMessage();
                    if (Thread.currentThread().isInterrupted()) break;
                }
            } finally {
                receiverDone.set(true);
            }
        }, "receiver");

        // Thread 2 – Decriptor: drains rawQueue after receiver finishes
        decriptorThread = new Thread(() -> {
            try {
                while (true) {
                    byte[] raw = rawQueue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                    if (raw == null) {
                        if (receiverDone.get()) break;
                        continue;
                    }
                    decriptor.decrypt(raw);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                decriptorDone.set(true);
            }
        }, "decriptor");

        // Thread 3 – Processor
        processorThread = new Thread(() -> {
            try {
                while (true) {
                    WarehouseCommand cmd = commandQueue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                    if (cmd == null) {
                        if (decriptorDone.get()) break;
                        continue;
                    }
                    processor.process(cmd);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                processorDone.set(true);
            }
        }, "processor");

        // Thread 4 – Encriptor
        encriptorThread = new Thread(() -> {
            try {
                while (true) {
                    CommandResponse resp = responseQueue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                    if (resp == null) {
                        if (processorDone.get()) break;
                        continue;
                    }
                    byte[] packet = encriptor.encrypt(resp);
                    if (packet.length > 0) outQueue.put(packet);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                encriptorDone.set(true);
            }
        }, "encriptor");

        // Thread 5 – Sender
        senderThread = new Thread(() -> {
            try {
                while (true) {
                    byte[] packet = outQueue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                    if (packet == null) {
                        if (encriptorDone.get()) break;
                        continue;
                    }
                    sender.sendMessage(packet, null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sender");

        receiverThread.start();
        decriptorThread.start();
        processorThread.start();
        encriptorThread.start();
        senderThread.start();
    }

    /**
     * Signals the receiver thread to stop; downstream threads drain
     * automatically and exit once their input queues are empty.
     */
    public void stop() {
        receiverThread.interrupt();
    }

    /** Blocks until all five threads have finished (or timeout elapses). */
    public void awaitTermination(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (Thread t : new Thread[]{receiverThread, decriptorThread,
                                     processorThread, encriptorThread, senderThread}) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining > 0) t.join(remaining);
        }
    }
}
