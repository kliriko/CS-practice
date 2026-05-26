package warehouse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Warehouse {
    private final ConcurrentHashMap<Integer, Product> products = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ProductGroup> groups = new ConcurrentHashMap<>();
    private final AtomicInteger nextProductId = new AtomicInteger(1);
    private final AtomicInteger nextGroupId = new AtomicInteger(1);

    public int addGroup(String name) {
        int id = nextGroupId.getAndIncrement();
        groups.put(id, new ProductGroup(id, name));
        return id;
    }

    public int addProduct(int groupId, String name) {
        ProductGroup group = groups.get(groupId);
        if (group == null) throw new IllegalArgumentException("Group not found: " + groupId);
        int id = nextProductId.getAndIncrement();
        Product product = new Product(id, groupId, name);
        products.put(id, product);
        group.productIds.add(id);
        return id;
    }

    public int getStock(int productId) {
        return product(productId).quantity.get();
    }

    public int creditStock(int productId, int quantity) {
        return product(productId).quantity.addAndGet(quantity);
    }

    public int debitStock(int productId, int quantity) {
        return product(productId).quantity.updateAndGet(current -> {
            if (current < quantity) throw new IllegalStateException("Insufficient stock");
            return current - quantity;
        });
    }

    public void setPrice(int productId, double price) {
        product(productId).price = price;
    }

    public double getPrice(int productId) {
        return product(productId).price;
    }

    private Product product(int id) {
        Product p = products.get(id);
        if (p == null) throw new IllegalArgumentException("Product not found: " + id);
        return p;
    }
}
