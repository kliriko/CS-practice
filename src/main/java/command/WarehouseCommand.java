package command;

public abstract class WarehouseCommand {

    public enum Type {
        GET_STOCK(1), DEBIT_STOCK(2), CREDIT_STOCK(3),
        ADD_GROUP(4), ADD_PRODUCT(5), SET_PRICE(6);

        public final int code;
        Type(int code) { this.code = code; }

        public static Type fromCode(int code) {
            for (Type t : values()) if (t.code == code) return t;
            throw new IllegalArgumentException("Unknown command type: " + code);
        }
    }

    public final long packetId;
    public final int userId;
    public final Type type;

    protected WarehouseCommand(long packetId, int userId, Type type) {
        this.packetId = packetId;
        this.userId = userId;
        this.type = type;
    }

    public static final class GetStock extends WarehouseCommand {
        public final int productId;
        public GetStock(long packetId, int userId, int productId) {
            super(packetId, userId, Type.GET_STOCK);
            this.productId = productId;
        }
    }

    public static final class DebitStock extends WarehouseCommand {
        public final int productId;
        public final int quantity;
        public DebitStock(long packetId, int userId, int productId, int quantity) {
            super(packetId, userId, Type.DEBIT_STOCK);
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static final class CreditStock extends WarehouseCommand {
        public final int productId;
        public final int quantity;
        public CreditStock(long packetId, int userId, int productId, int quantity) {
            super(packetId, userId, Type.CREDIT_STOCK);
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static final class AddGroup extends WarehouseCommand {
        public final String groupName;
        public AddGroup(long packetId, int userId, String groupName) {
            super(packetId, userId, Type.ADD_GROUP);
            this.groupName = groupName;
        }
    }

    public static final class AddProduct extends WarehouseCommand {
        public final int groupId;
        public final String productName;
        public AddProduct(long packetId, int userId, int groupId, String productName) {
            super(packetId, userId, Type.ADD_PRODUCT);
            this.groupId = groupId;
            this.productName = productName;
        }
    }

    public static final class SetPrice extends WarehouseCommand {
        public final int productId;
        public final double price;
        public SetPrice(long packetId, int userId, int productId, double price) {
            super(packetId, userId, Type.SET_PRICE);
            this.productId = productId;
            this.price = price;
        }
    }
}
