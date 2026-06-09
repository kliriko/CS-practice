package transport;

import command.CommandResponse;
import command.WarehouseCommand;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;
import protocol.ProtocolSender;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class StoreClientTCP extends Thread {

    static final int PRODUCT_ID = 1;
    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();

    private final int id;
    private final ProtocolParser parser = new ProtocolParser();
    private final ProtocolSender sender = new ProtocolSender();
    private long packetId = 1;

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            new StoreClientTCP(i).start();
            Thread.sleep(100);
        }
    }

    StoreClientTCP(int id) {
        this.id = id;
    }

    public void run() {
        while (true) {
            try (Socket socket = new Socket("localhost", StoreServerTCP.PORT)) {
                System.out.printf("[TCP Client %d] Connected%n", id);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                for (int i = 0; i < 10; i++) {
                    byte[] body = ByteBuffer.allocate(8).putInt(PRODUCT_ID).putInt(1).array();
                    CommandResponse r = send(in, out, WarehouseCommand.Type.DEBIT_STOCK, body);
                    System.out.printf("[TCP Client %d] debit #%d success=%b%n", id, i + 1, r.success);
                    Thread.sleep(300);
                }

                byte[] body = ByteBuffer.allocate(4).putInt(PRODUCT_ID).array();
                CommandResponse r = send(in, out, WarehouseCommand.Type.GET_STOCK, body);
                System.out.printf("[TCP Client %d] stock=%d%n", id, r.intData);
                return;

            } catch (IOException e) {
                System.out.printf("[TCP Client %d] Server unavailable — retrying in 2s...%n", id);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                System.err.printf("[TCP Client %d] Error: %s%n", id, e.getMessage());
                return;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private CommandResponse send(InputStream in, OutputStream out,
                                 WarehouseCommand.Type type, byte[] body) throws Exception {
        byte[] packet = sender.sendPacket((byte) 0x01, packetId++, type.code, id, body, AES_KEY);
        out.write(packet);
        out.flush();
        return parseResponse(StoreServerTCP.readPacket(in));
    }

    private CommandResponse parseResponse(byte[] raw) throws Exception {
        ProtocolPayload p = parser.receivePacket(raw);
        byte[] dec = p.message.decrypt(AES_KEY);
        boolean success = dec[0] == 1;
        int intData = ByteBuffer.wrap(dec, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        String msg = new String(dec, 5, dec.length - 5, StandardCharsets.UTF_8);
        return CommandResponse.of(p.bPktId, p.message.bUserId, success, msg, intData);
    }
}
