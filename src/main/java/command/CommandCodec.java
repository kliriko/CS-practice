package command;

import protocol.ProtocolPayload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CommandCodec {

    public static WarehouseCommand decode(ProtocolPayload payload) {
        long pktId = payload.bPktId;
        int userId = payload.message.bUserId;
        WarehouseCommand.Type type = WarehouseCommand.Type.fromCode(payload.message.cType);
        ByteBuffer buf = ByteBuffer.wrap(payload.message.message);

        return switch (type) {
            case GET_STOCK   -> new WarehouseCommand.GetStock(pktId, userId, buf.getInt());
            case DEBIT_STOCK -> new WarehouseCommand.DebitStock(pktId, userId, buf.getInt(), buf.getInt());
            case CREDIT_STOCK -> new WarehouseCommand.CreditStock(pktId, userId, buf.getInt(), buf.getInt());
            case ADD_GROUP   -> {
                int len = buf.getInt();
                byte[] b = new byte[len]; buf.get(b);
                yield new WarehouseCommand.AddGroup(pktId, userId, new String(b, StandardCharsets.UTF_8));
            }
            case ADD_PRODUCT -> {
                int groupId = buf.getInt();
                int len = buf.getInt();
                byte[] b = new byte[len]; buf.get(b);
                yield new WarehouseCommand.AddProduct(pktId, userId, groupId, new String(b, StandardCharsets.UTF_8));
            }
            case SET_PRICE   -> new WarehouseCommand.SetPrice(pktId, userId, buf.getInt(), buf.getDouble());
        };
    }

    public static byte[] encodeGetStock(int productId) {
        return ByteBuffer.allocate(4).putInt(productId).array();
    }

    public static byte[] encodeDebitStock(int productId, int quantity) {
        return ByteBuffer.allocate(8).putInt(productId).putInt(quantity).array();
    }

    public static byte[] encodeCreditStock(int productId, int quantity) {
        return ByteBuffer.allocate(8).putInt(productId).putInt(quantity).array();
    }

    public static byte[] encodeAddGroup(String name) {
        byte[] b = name.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(4 + b.length).putInt(b.length).put(b).array();
    }

    public static byte[] encodeAddProduct(int groupId, String name) {
        byte[] b = name.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(8 + b.length).putInt(groupId).putInt(b.length).put(b).array();
    }

    public static byte[] encodeSetPrice(int productId, double price) {
        return ByteBuffer.allocate(12).putInt(productId).putDouble(price).array();
    }
}
