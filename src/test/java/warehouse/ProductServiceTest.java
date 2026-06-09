package warehouse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class ProductServiceTest extends BaseMySqlTest {

    private ProductService service;
    private int groupId;

    @BeforeEach
    void setUp() {
        service = new ProductService(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        groupId = service.addGroup("Напої");
    }

    @AfterEach
    void cleanUp() {
        service.deleteAll();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Test
    void create_insertsAndReturnsProduct() {
        Product p = service.create(groupId, "Кава");

        assertThat(p.name).isEqualTo("Кава");
        assertThat(p.groupId).isEqualTo(groupId);
        assertThat(p.quantity.get()).isEqualTo(0);
    }

    @Test
    void getProduct_returnsCorrectProduct() {
        Product created = service.create(groupId, "Чай");
        Product found = service.getProduct(created.id);

        assertThat(found.id).isEqualTo(created.id);
        assertThat(found.name).isEqualTo("Чай");
    }

    @Test
    void getProduct_unknownId_throws() {
        assertThatThrownBy(() -> service.getProduct(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_removesProduct() {
        Product p = service.create(groupId, "Вода");
        service.delete(p.id);

        assertThatThrownBy(() -> service.getProduct(p.id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditAndDebitStock() {
        Product p = service.create(groupId, "Сік");
        service.creditStock(p.id, 100);

        assertThat(service.getStock(p.id)).isEqualTo(100);

        service.debitStock(p.id, 30);

        assertThat(service.getStock(p.id)).isEqualTo(70);
    }

    @Test
    void debitStock_insufficientStock_throws() {
        Product p = service.create(groupId, "Кава");
        service.creditStock(p.id, 5);

        assertThatThrownBy(() -> service.debitStock(p.id, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void setPrice_persistsToDb() {
        Product p = service.create(groupId, "Кава");
        service.setPrice(p.id, 89.99);

        assertThat(service.getProduct(p.id).price).isEqualTo(89.99);
    }

    // ── Search — фільтри ──────────────────────────────────────────────────────

    @Test
    void search_noFilter_returnsAll() {
        service.create(groupId, "Кава");
        service.create(groupId, "Чай");
        service.create(groupId, "Вода");

        Page<Product> page = service.search(new ProductFilter(), 0, 10);

        assertThat(page.total).isEqualTo(3);
        assertThat(page.items).hasSize(3);
    }

    @Test
    void search_byName_caseInsensitive() {
        service.create(groupId, "Кава Арабіка");
        service.create(groupId, "Чай зелений");
        service.create(groupId, "Кава Робуста");

        ProductFilter filter = new ProductFilter();
        filter.name = "кава";
        Page<Product> page = service.search(filter, 0, 10);

        assertThat(page.total).isEqualTo(2);
        assertThat(page.items).allMatch(p -> p.name.toLowerCase().contains("кава"));
    }

    @Test
    void search_byGroupId() {
        int otherGroup = service.addGroup("Їжа");
        service.create(groupId, "Кава");
        service.create(otherGroup, "Хліб");
        service.create(otherGroup, "Масло");

        ProductFilter filter = new ProductFilter();
        filter.groupId = otherGroup;
        Page<Product> page = service.search(filter, 0, 10);

        assertThat(page.total).isEqualTo(2);
        assertThat(page.items).allMatch(p -> p.groupId == otherGroup);
    }

    @Test
    void search_byPriceRange() {
        Product p1 = service.create(groupId, "A"); service.setPrice(p1.id, 10.0);
        Product p2 = service.create(groupId, "B"); service.setPrice(p2.id, 50.0);
        Product p3 = service.create(groupId, "C"); service.setPrice(p3.id, 200.0);

        ProductFilter filter = new ProductFilter();
        filter.minPrice = 30.0;
        filter.maxPrice = 100.0;
        Page<Product> page = service.search(filter, 0, 10);

        assertThat(page.total).isEqualTo(1);
        assertThat(page.items.get(0).name).isEqualTo("B");
    }

    @Test
    void search_byQuantityRange() {
        Product p1 = service.create(groupId, "A"); service.creditStock(p1.id, 5);
        Product p2 = service.create(groupId, "B"); service.creditStock(p2.id, 50);
        Product p3 = service.create(groupId, "C"); service.creditStock(p3.id, 500);

        ProductFilter filter = new ProductFilter();
        filter.minQuantity = 10;
        filter.maxQuantity = 100;
        Page<Product> page = service.search(filter, 0, 10);

        assertThat(page.total).isEqualTo(1);
        assertThat(page.items.get(0).name).isEqualTo("B");
    }

    @Test
    void search_multipleFilters() {
        int foodGroup = service.addGroup("Їжа");

        Product p1 = service.create(groupId, "Кава");         service.setPrice(p1.id, 120.0);
        Product p2 = service.create(groupId, "Кава дешева");  service.setPrice(p2.id, 30.0);
        Product p3 = service.create(foodGroup, "Кава з їжею"); service.setPrice(p3.id, 120.0);

        ProductFilter filter = new ProductFilter();
        filter.name = "кава";
        filter.groupId = groupId;
        filter.minPrice = 100.0;
        Page<Product> page = service.search(filter, 0, 10);

        assertThat(page.total).isEqualTo(1);
        assertThat(page.items.get(0).id).isEqualTo(p1.id);
    }

    // ── Пагінація ─────────────────────────────────────────────────────────────

    @Test
    void search_pagination_firstPage() {
        for (int i = 0; i < 7; i++) service.create(groupId, "Товар " + i);

        Page<Product> page = service.search(new ProductFilter(), 0, 3);

        assertThat(page.total).isEqualTo(7);
        assertThat(page.items).hasSize(3);
    }

    @Test
    void search_pagination_lastPartialPage() {
        for (int i = 0; i < 7; i++) service.create(groupId, "Товар " + i);

        Page<Product> page = service.search(new ProductFilter(), 2, 3);

        assertThat(page.total).isEqualTo(7);
        assertThat(page.items).hasSize(1);
    }

    @Test
    void search_pagination_beyondEnd_returnsEmpty() {
        service.create(groupId, "Кава");

        Page<Product> page = service.search(new ProductFilter(), 5, 10);

        assertThat(page.total).isEqualTo(1);
        assertThat(page.items).isEmpty();
    }
}
