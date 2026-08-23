package com.lorongtea.order;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.lorongtea.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mvc;

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

    @Test
    void replaysConcurrentCheckoutAndRejectsChangedIntents() throws Exception {
        String customer = token("demo-customer");
        long product = product("Pearl tea", 500, 4);
        String key = UUID.randomUUID().toString();
        String split = """
                {"storeId":"1","items":[{"productId":"%d","quantity":1},{"productId":"%d","quantity":2}],
                "expectedTotalCents":1500}
                """.formatted(product, product);
        String merged = checkoutBody(product, 3, 1500);

        var results = race(() -> mvc.perform(place(customer, key, split)).andReturn().getResponse(),
                () -> mvc.perform(place(customer, key, merged)).andReturn().getResponse());
        assertThat(results).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(201, 200);
        String id = JsonPath.read(results.getFirst().getContentAsString(), "$.data.id");
        assertThat(JsonPath.<String>read(results.getLast().getContentAsString(), "$.data.id")).isEqualTo(id);

        mvc.perform(place(customer, key, merged)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(place(customer, key, checkoutBody(product, 3, 1501)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(quantity(product)).isEqualTo(1);
    }

    @Test
    void cancellationAndPreparationHaveOneWinnerAndStockReturnsOnce() throws Exception {
        String customer = token("demo-customer");
        String staff = token("demo-bb-staff");
        long product = product("Milk tea", 650, 4);
        String id = placeOrder(customer, product, 2, 1300);
        String cancel = "/api/v1/orders/" + id + "/cancel";
        String start = staffOrder(id) + "/start";

        var result = race(() -> mvc.perform(authenticatedPost(cancel, customer)).andReturn().getResponse(),
                () -> mvc.perform(authenticatedPost(start, staff)).andReturn().getResponse());
        assertThat(result).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(200, 409);
        var winning = result.stream().filter(response -> response.getStatus() == 200).findFirst().orElseThrow();
        boolean cancelled = "CANCELLED".equals(JsonPath.<String>read(winning.getContentAsString(), "$.data.status"));
        assertThat(quantity(product)).isEqualTo(cancelled ? 4 : 2);

        String retryId = placeOrder(customer, product, 1, 650);
        String retryPath = "/api/v1/orders/" + retryId + "/cancel";
        int stockBeforeRestore = quantity(product);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(authenticatedPost(retryPath, customer)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }
        assertThat(quantity(product)).isEqualTo(stockBeforeRestore + 1);
    }

    private String placeOrder(String bearer, long productId, int quantity, long total) throws Exception {
        String response = mvc.perform(place(bearer, UUID.randomUUID().toString(), checkoutBody(productId, quantity, total)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, String bearer) {
        return post(path).header("Authorization", bearer);
    }

    private String staffOrder(String id) {
        return "/api/v1/staff/stores/1/orders/" + id;
    }

    private MockHttpServletRequestBuilder place(
            String bearer, String key, String body) {
        return post("/api/v1/orders").header("Authorization", bearer).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String checkoutBody(long productId, int quantity, long total) {
        return """
                {"storeId":"1","items":[{"productId":"%d","quantity":%d}],"expectedTotalCents":%d}
                """.formatted(productId, quantity, total);
    }

    private String token(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"integration-test-password"}
                                """.formatted(username)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.data.token");
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
