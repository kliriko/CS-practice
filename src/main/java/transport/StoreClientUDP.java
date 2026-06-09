package transport;

import command.CommandResponse;
import command.WarehouseCommand;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;
import protocol.ProtocolSender;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StoreClientUDP extends Thread {

    private static final int PRODUCT_ID = 1;
    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();
    private static final int MAX_PACKET = 65507;
    private static final int TIMEOUT_MS = 2_000;
    private static final int MAX_RETRIES = 5;

    private final int id;
    private final ProtocolParser parser = new ProtocolParser();
    private final ProtocolSender sender = new ProtocolSender();
    private long packetId = 1;

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            new StoreClientUDP(i).start();
            Thread.sleep(100);
        }
    }

    StoreClientUDP(int id) {
        this.id = id;
    }

    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress server = InetAddress.getByName("localhost");

            for (int i = 0; i < 10; i++) {
                byte[] body = ByteBuffer.allocate(8).putInt(PRODUCT_ID).putInt(1).array();
                CommandResponse r = sendCommand(socket, server, WarehouseCommand.Type.DEBIT_STOCK, body);
                System.out.printf("[UDP Client %d] debit #%d success=%b%n", id, i + 1, r.success);
                Thread.sleep(300);
            }

            byte[] body = ByteBuffer.allocate(4).putInt(PRODUCT_ID).array();
            CommandResponse r = sendCommand(socket, server, WarehouseCommand.Type.GET_STOCK, body);
            System.out.printf("[UDP Client %d] stock=%d%n", id, r.intData);

        } catch (Exception e) {
            System.err.printf("[UDP Client %d] Error: %s%n", id, e.getMessage());
        }
    }

    private CommandResponse sendCommand(DatagramSocket socket, InetAddress server,
                                        WarehouseCommand.Type type, byte[] body) throws Exception {
        long id = packetId++;
        byte[] packet = sender.sendPacket((byte) 0x01, id, type.code, this.id, body, AES_KEY);
        DatagramPacket request = new DatagramPacket(packet, packet.length, server, StoreServerUDP.PORT);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            socket.send(request);
            System.out.printf("[UDP Client %d] pktId=%d attempt=%d%n", this.id, id, attempt);

            try {
                while (true) {
                    byte[] buf = new byte[MAX_PACKET];
                    DatagramPacket response = new DatagramPacket(buf, buf.length);
                    socket.receive(response);

                    byte[] data = Arrays.copyOfRange(response.getData(), 0, response.getLength());
                    ProtocolPayload p = parser.receivePacket(data);

                    if (p.bPktId != id) {
                        System.out.printf("[UDP Client %d] Stale pktId=%d (expected %d), ignoring%n",
                                this.id, p.bPktId, id);
                        continue;
                    }
                    return parseResponse(p);
                }
            } catch (SocketTimeoutException e) {
                System.out.printf("[UDP Client %d] Timeout pktId=%d, retrying...%n", this.id, id);
            }
        }

        throw new Exception("No response after " + MAX_RETRIES + " attempts (pktId=" + id + ")");
    }

    private CommandResponse parseResponse(ProtocolPayload p) throws Exception {
        byte[] dec = p.message.decrypt(AES_KEY);
        boolean success = dec[0] == 1;
        int intData = ByteBuffer.wrap(dec, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        String msg = new String(dec, 5, dec.length - 5, StandardCharsets.UTF_8);
        return CommandResponse.of(p.bPktId, p.message.bUserId, success, msg, intData);
    }
}
