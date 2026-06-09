package warehouse;

import java.util.List;

public class Page<T> {
    public final List<T> items;
    public final int total;
    public final int page;
    public final int pageSize;

    public Page(List<T> items, int total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
