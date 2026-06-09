package transport;

import command.CommandCodec;
import command.CommandResponse;
import command.WarehouseCommand;
import pipeline.ResponseEncriptor;
import protocol.ProtocolParser;
import protocol.ProtocolPayload;
import warehouse.Warehouse;

import java.net.*;
import java.util.Arrays;

public class StoreServerUDP extends Thread {

    static final int PORT = 9002;
    private static final byte[] AES_KEY = "0123456789abcdef".getBytes();
    private static final int MAX_PACKET = 65507;
    private static final Warehouse warehouse = new Warehouse();

    private final DatagramSocket socket;

    public static void main(String[] args) throws Exception {
        int groupId = warehouse.addGroup("Напої");
        int coffee = warehouse.addProduct(groupId, "Кава");
        warehouse.creditStock(coffee, 1000);
        System.out.printf("Склад: group=%d, coffee(id=%d)=1000%n%n", groupId, coffee);

        new StoreServerUDP();
    }

    StoreServerUDP() throws SocketException {
        socket = new DatagramSocket(PORT);
        socket.setReuseAddress(true);
        System.out.println("UDP Server started on port " + PORT);
        start();
    }

    public void run() {
        while (true) {
            byte[] buf = new byte[MAX_PACKET];
            DatagramPacket dgram = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(dgram);
                byte[] data = Arrays.copyOfRange(dgram.getData(), 0, dgram.getLength());
                InetAddress addr = dgram.getAddress();
                int port = dgram.getPort();
                handleDatagram(data, addr, port);
            } catch (Exception e) {
                System.err.println("[UDP Server] Error: " + e.getMessage());
            }
        }
    }

    protected void handleDatagram(byte[] data, InetAddress addr, int port) {
        try {
            ProtocolParser parser = new ProtocolParser();
            ResponseEncriptor encriptor = new ResponseEncriptor();

            ProtocolPayload payload = parser.receivePacket(data);
            payload.message.message = payload.message.decrypt(AES_KEY);

            CommandResponse response = execute(CommandCodec.decode(payload));
            byte[] packet = encriptor.encrypt(response);
            socket.send(new DatagramPacket(packet, packet.length, addr, port));
        } catch (Exception e) {
            System.err.println("[UDP Server] Handle error: " + e.getMessage());
        }
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
}
