package transport;

import command.CommandCodec;
import command.CommandResponse;
import command.WarehouseCommand;
import pipeline.ResponseEncriptor;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;
import warehouse.Warehouse;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class StoreServerTCP {

    static final int PORT = 9001;

    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();
    private static final Warehouse warehouse = new Warehouse();

    public static void main(String[] args) throws IOException {
        int groupId = warehouse.addGroup("Напої");
        int coffee = warehouse.addProduct(groupId, "Кава");
        warehouse.creditStock(coffee, 1000);
        System.out.printf("Склад: group=%d, coffee(id=%d)=1000%n%n", groupId, coffee);

        try (ServerSocket s = new ServerSocket(PORT)) {
            s.setReuseAddress(true);
            System.out.println("TCP Server started on port " + PORT);
            while (true) {
                Socket socket = s.accept();
                System.out.println("[TCP Server] Client: " + socket.getRemoteSocketAddress());
                new ClientHandler(socket);
            }
        }
    }

    static byte[] readPacket(InputStream in) throws IOException {
        byte[] header = in.readNBytes(16);
        if (header.length < 16) throw new EOFException("Connection closed");
        int wLen = ByteBuffer.wrap(header, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        byte[] rest = in.readNBytes(wLen + 2);
        if (rest.length < wLen + 2) throw new EOFException("Connection closed");
        byte[] full = new byte[header.length + rest.length];
        System.arraycopy(header, 0, full, 0, header.length);
        System.arraycopy(rest, 0, full, header.length, rest.length);
        return full;
    }

    private static CommandResponse execute(WarehouseCommand cmd) {
        try {
            return switch (cmd.type) {
                case GET_STOCK -> {
                    int qty = warehouse.getStock(((WarehouseCommand.GetStock) cmd).productId);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, qty);
                }
                case DEBIT_STOCK -> {
                    var c = (WarehouseCommand.DebitStock) cmd;
                    warehouse.debitStock(c.productId, c.quantity);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
                case CREDIT_STOCK -> {
                    var c = (WarehouseCommand.CreditStock) cmd;
                    warehouse.creditStock(c.productId, c.quantity);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
                case ADD_GROUP -> {
                    int id = warehouse.addGroup(((WarehouseCommand.AddGroup) cmd).groupName);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, id);
                }
                case ADD_PRODUCT -> {
                    var c = (WarehouseCommand.AddProduct) cmd;
                    int id = warehouse.addProduct(c.groupId, c.productName);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, id);
                }
                case SET_PRICE -> {
                    var c = (WarehouseCommand.SetPrice) cmd;
                    warehouse.setPrice(c.productId, c.price);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
            };
        } catch (Exception e) {
            return CommandResponse.error(cmd.packetId, cmd.userId, e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private final ProtocolParser parser = new ProtocolParser();
        private final ResponseEncriptor encriptor = new ResponseEncriptor();

        ClientHandler(Socket socket) {
            this.socket = socket;
            start();
        }

        public void run() {
            try (socket) {
                socket.setSoTimeout(30_000);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    ProtocolPayload payload = parser.receivePacket(readPacket(in));
                    payload.message.message = payload.message.decrypt(AES_KEY);
                    byte[] resp = encriptor.encrypt(execute(CommandCodec.decode(payload)));
                    out.write(resp);
                    out.flush();
                }
            } catch (EOFException | SocketException e) {
                System.out.println("[TCP Server] Client disconnected: " + socket.getRemoteSocketAddress());
            } catch (Exception e) {
                System.err.println("[TCP Server] Error: " + e.getMessage());
            }
        }
    }
}
