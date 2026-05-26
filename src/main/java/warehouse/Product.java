package warehouse;

import java.util.concurrent.atomic.AtomicInteger;

public class Product {
    public final int id;
    public final int groupId;
    public final String name;
    public final AtomicInteger quantity = new AtomicInteger(0);
    public volatile double price = 0.0;

    public Product(int id, int groupId, String name) {
        this.id = id;
        this.groupId = groupId;
        this.name = name;
    }
}
