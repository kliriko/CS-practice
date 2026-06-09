package warehouse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductService {

    private final Warehouse warehouse;
    private final Connection connection;

    public ProductService(Warehouse warehouse) {
        this.warehouse = warehouse;
        this.connection = null;
    }

    public ProductService(String url, String user, String password) {
        this.warehouse = null;
        try {
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new RuntimeException("Can't connect to DB", e);
        }
        initTables();
    }

    public int addGroup(String name) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO product_group(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
                throw new RuntimeException("Insert group failed");
            } catch (SQLException e) {
                throw new RuntimeException("Can't add group", e);
            }
        }
        return warehouse.addGroup(name);
    }

    public Product create(int groupId, String name) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO product(group_id, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, groupId);
                ps.setString(2, name);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return getProduct(keys.getInt(1));
                throw new RuntimeException("Insert product failed");
            } catch (SQLException e) {
                throw new RuntimeException("Can't create product", e);
            }
        }
        int id = warehouse.addProduct(groupId, name);
        return warehouse.getProduct(id);
    }

    public Product getProduct(int id) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM product WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapProduct(rs);
                }
                throw new IllegalArgumentException("Product not found: " + id);
            } catch (SQLException e) {
                throw new RuntimeException("Can't get product", e);
            }
        }
        return warehouse.getProduct(id);
    }

    public void delete(int id) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM product WHERE id = ?")) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows == 0) throw new IllegalArgumentException("Product not found: " + id);
            } catch (SQLException e) {
                throw new RuntimeException("Can't delete product", e);
            }
            return;
        }
        warehouse.removeProduct(id);
    }

    public int getStock(int productId) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT quantity FROM product WHERE id = ?")) {
                ps.setInt(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("quantity");
                }
                throw new IllegalArgumentException("Product not found: " + productId);
            } catch (SQLException e) {
                throw new RuntimeException("Can't get stock", e);
            }
        }
        return warehouse.getStock(productId);
    }

    public void creditStock(int productId, int quantity) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE product SET quantity = quantity + ? WHERE id = ?")) {
                ps.setInt(1, quantity);
                ps.setInt(2, productId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Can't credit stock", e);
            }
            return;
        }
        warehouse.creditStock(productId, quantity);
    }

    public void debitStock(int productId, int quantity) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE product SET quantity = quantity - ? WHERE id = ? AND quantity >= ?")) {
                ps.setInt(1, quantity);
                ps.setInt(2, productId);
                ps.setInt(3, quantity);
                int rows = ps.executeUpdate();
                if (rows == 0) throw new IllegalStateException("Insufficient stock");
            } catch (SQLException e) {
                throw new RuntimeException("Can't debit stock", e);
            }
            return;
        }
        warehouse.debitStock(productId, quantity);
    }

    public void setPrice(int productId, double price) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE product SET price = ? WHERE id = ?")) {
                ps.setDouble(1, price);
                ps.setInt(2, productId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Can't set price", e);
            }
            return;
        }
        warehouse.setPrice(productId, price);
    }

    public Page<Product> search(ProductFilter filter, int page, int pageSize) {
        if (connection != null) return searchDb(filter, page, pageSize);
        return searchMemory(filter, page, pageSize);
    }

    public void deleteAll() {
        if (connection != null) {
            try (Statement st = connection.createStatement()) {
                st.execute("DELETE FROM product");
                st.execute("DELETE FROM product_group");
            } catch (SQLException e) {
                throw new RuntimeException("Can't delete all", e);
            }
        }
    }

    private Page<Product> searchDb(ProductFilter filter, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (filter.name != null) { where.append(" AND name LIKE ?"); params.add("%" + filter.name + "%"); }
        if (filter.groupId != null) { where.append(" AND group_id = ?"); params.add(filter.groupId); }
        if (filter.minQuantity != null) { where.append(" AND quantity >= ?"); params.add(filter.minQuantity); }
        if (filter.maxQuantity != null) { where.append(" AND quantity <= ?"); params.add(filter.maxQuantity); }
        if (filter.minPrice != null) { where.append(" AND price >= ?"); params.add(filter.minPrice); }
        if (filter.maxPrice != null) { where.append(" AND price <= ?"); params.add(filter.maxPrice); }

        int total = countQuery("SELECT COUNT(*) FROM product" + where, params);

        List<Product> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM product" + where + " LIMIT ? OFFSET ?")) {
            int i = 1;
            for (Object p : params) setParam(ps, i++, p);
            ps.setInt(i++, pageSize);
            ps.setInt(i, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(mapProduct(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Can't search products", e);
        }
        return new Page<>(items, total, page, pageSize);
    }

    private Page<Product> searchMemory(ProductFilter filter, int page, int pageSize) {
        List<Product> filtered = warehouse.getAllProducts().stream()
                .filter(p -> filter.name == null || p.name.toLowerCase().contains(filter.name.toLowerCase()))
                .filter(p -> filter.groupId == null || p.groupId == filter.groupId)
                .filter(p -> filter.minQuantity == null || p.quantity.get() >= filter.minQuantity)
                .filter(p -> filter.maxQuantity == null || p.quantity.get() <= filter.maxQuantity)
                .filter(p -> filter.minPrice == null || p.price >= filter.minPrice)
                .filter(p -> filter.maxPrice == null || p.price <= filter.maxPrice)
                .toList();

        int total = filtered.size();
        int from = page * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Product> items = from >= total ? List.of() : filtered.subList(from, to);
        return new Page<>(items, total, page, pageSize);
    }

    private int countQuery(String sql, List<Object> params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) setParam(ps, i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Can't count", e);
        }
    }

    private void setParam(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof String s) ps.setString(index, s);
        else if (value instanceof Integer n) ps.setInt(index, n);
        else if (value instanceof Double d) ps.setDouble(index, d);
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product(rs.getInt("id"), rs.getInt("group_id"), rs.getString("name"));
        p.quantity.set(rs.getInt("quantity"));
        p.price = rs.getDouble("price");
        return p;
    }

    private void initTables() {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS product_group (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(100) NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS product (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        group_id INT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        quantity INT NOT NULL DEFAULT 0,
                        price DOUBLE NOT NULL DEFAULT 0.0
                    )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("DB init error", e);
        }
    }
}
