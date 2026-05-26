package warehouse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProductGroup {
    public final int id;
    public final String name;
    public final List<Integer> productIds = new CopyOnWriteArrayList<>();

    public ProductGroup(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
