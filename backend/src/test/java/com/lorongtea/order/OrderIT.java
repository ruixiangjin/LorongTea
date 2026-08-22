package com.lorongtea.order;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lorongtea.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "LORONG_JWT_SECRET=dGhpcy1pcy1hLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXk=",
        "LORONG_DEMO_PASSWORD=integration-test-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class OrderIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private CheckoutTransaction checkout;

    @Autowired
    private OrderMapper orders;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void snapshotsTheSaleAndRollsBackEarlierDeductionsWhenALaterItemFails() {
        long customer = userId("demo-customer");
        long first = product("Boba", 800, 3);
        long empty = product("Oolong", 900, 0);

        String failedKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> checkout.create(customer, 1, failedKey, "0".repeat(64),
                new TreeMap<>(Map.of(first, 1, empty, 1)), 1700))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("OUT_OF_STOCK"));
        assertThat(quantity(first)).isEqualTo(3);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders WHERE idempotency_key = :key")
                .param("key", failedKey).query(Integer.class).single()).isZero();

        long orderId = checkout.create(customer, 1, UUID.randomUUID().toString(), "0".repeat(64),
                new TreeMap<>(Map.of(first, 2)), 1600);
        jdbc.sql("UPDATE store_menu_item SET price_cents = 1000 WHERE store_id = 1 AND product_id = :product")
                .param("product", first).update();
        assertThat(quantity(first)).isEqualTo(1);
        assertThat(orders.findItems(orderId)).containsExactly(new OrderItem(first, "Boba", 800, 2));
        assertThat(jdbc.sql("SELECT paid_at IS NULL AND paid_by IS NULL FROM orders WHERE id = :id")
                .param("id", orderId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void sellsTheLastUnitOnceAcrossConcurrentOrders() throws Exception {
        long customer = userId("demo-customer");
        long product = product("Last cup", 850, 1);
        Callable<String> attempt = () -> {
            try {
                checkout.create(customer, 1, UUID.randomUUID().toString(), "0".repeat(64),
                        new TreeMap<>(Map.of(product, 1)), 850);
                return "CREATED";
            } catch (ApiException error) {
                return error.code();
            }
        };
        assertThat(race(attempt, attempt)).containsExactlyInAnyOrder("CREATED", "OUT_OF_STOCK");
        assertThat(quantity(product)).isZero();
    }

    private long userId(String username) {
        return jdbc.sql("SELECT id FROM app_user WHERE username = :username")
                .param("username", username).query(Long.class).single();
    }

    private long product(String name, long priceCents, int quantity) {
        String sku = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO product (sku_code, name) VALUES (:sku, :name)")
                .param("sku", sku).param("name", name).update();
        long id = jdbc.sql("SELECT id FROM product WHERE sku_code = :sku")
                .param("sku", sku).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO store_menu_item (store_id, product_id, price_cents, available_qty)
                VALUES (1, :product, :price, :quantity)
                """).param("product", id).param("price", priceCents).param("quantity", quantity).update();
        return id;
    }

    private int quantity(long productId) {
        return jdbc.sql("SELECT available_qty FROM store_menu_item WHERE store_id = 1 AND product_id = :product")
                .param("product", productId).query(Integer.class).single();
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var left = threads.submit(() -> {
                start.await();
                return first.call();
            });
            var right = threads.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
    }
}
