package com.lorongtea.order;

import java.time.Instant;
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
import com.lorongtea.auth.AccountIdentity;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private PickupRedemptionService redemption;

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
        String stalePriceKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> checkout.create(customer, 1, stalePriceKey, "0".repeat(64),
                new TreeMap<>(Map.of(first, 1)), 800))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("PRICE_CHANGED"));
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

    @Test
    void recordsTheFirstConcurrentPaymentAndShowsPickupOnlyAfterPayment() throws Exception {
        String customer = token("demo-customer");
        String firstStaff = token("demo-bb-staff");
        String secondStaff = token("demo-bb-staff-2");
        long product = product("Payment tea", 990, 2);
        String id = placeOrder(customer, product, 1, 990);
        String detail = "/api/v1/orders/" + id;
        String confirm = staffOrder(id) + "/confirm-payment";

        mvc.perform(authenticatedPost(confirm, firstStaff)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        makeReady(id, firstStaff);
        String pickup = pickupToken(id);
        mvc.perform(authenticatedPost(staffOrder(id) + "/ready", secondStaff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupToken").doesNotHaveJsonPath());
        assertThat(pickupToken(id)).isEqualTo(pickup).matches("[A-Za-z0-9_-]{22}");

        mvc.perform(get(detail).header("Authorization", customer))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.pickupToken").doesNotHaveJsonPath());
        long firstStaffId = userId("demo-bb-staff");
        var firstAccount = new AccountIdentity(firstStaffId, AccountIdentity.Role.STAFF, 1L);
        assertThatThrownBy(() -> redemption.redeem(firstAccount, 1, pickup))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("PAYMENT_REQUIRED"));

        var confirmations = race(() -> payment(confirm, firstStaff), () -> payment(confirm, secondStaff));
        assertThat(confirmations).extracting(body -> JsonPath.<String>read(body, "$.data.result"))
                .containsExactlyInAnyOrder("CONFIRMED", "ALREADY_PAID");
        String paidBy = JsonPath.read(confirmations.getFirst(), "$.data.paidBy");
        String paidAt = JsonPath.read(confirmations.getFirst(), "$.data.paidAt");
        assertThat(Instant.parse(paidAt)).isNotNull();
        assertThat(paidBy).isEqualTo("CONFIRMED".equals(JsonPath.<String>read(confirmations.getFirst(), "$.data.result"))
                ? Long.toString(firstStaffId) : Long.toString(userId("demo-bb-staff-2")));
        assertThat(JsonPath.<String>read(confirmations.getLast(), "$.data.paidBy")).isEqualTo(paidBy);
        assertThat(JsonPath.<String>read(confirmations.getLast(), "$.data.paidAt")).isEqualTo(paidAt);

        mvc.perform(get(detail).header("Authorization", customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.pickupToken").value(pickup))
                .andExpect(jsonPath("$.data.paidBy").doesNotHaveJsonPath());
        mvc.perform(get(staffOrder(id)).header("Authorization", secondStaff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidBy").value(paidBy))
                .andExpect(jsonPath("$.data.pickupToken").doesNotHaveJsonPath());
    }

    @Test
    void listsOrdersInStableOrderAndHidesOrdersOutsideTheCallerScope() throws Exception {
        jdbc.sql("""
                INSERT INTO app_user (username, password_hash, role)
                SELECT 'other-customer', password_hash, role FROM app_user WHERE username = 'demo-customer'
                """).update();
        String owner = token("other-customer");
        String other = token("demo-customer");
        String staff = token("demo-bb-staff");
        String otherStore = token("demo-pj-staff");
        long product = product("History tea", 750, 2);
        String first = placeOrder(owner, product, 1, 750);
        String second = placeOrder(owner, product, 1, 750);
        jdbc.sql("UPDATE orders SET created_at = '2000-01-01 00:00:00' WHERE customer_id = :owner")
                .param("owner", userId("other-customer")).update();

        mvc.perform(get("/api/v1/orders").header("Authorization", owner).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(second));
        mvc.perform(get("/api/v1/orders").header("Authorization", owner).param("limit", "1").param("offset", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(first));
        mvc.perform(get("/api/v1/orders/" + first).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].productName").value("History tea"));
        mvc.perform(get("/api/v1/orders/" + first).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders").header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(authenticatedPost("/api/v1/staff/stores/2/orders/" + first + "/start", otherStore))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/staff/stores/1/orders").header("Authorization", staff)
                        .param("status", "CREATED").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(first));
        mvc.perform(get("/api/v1/staff/stores/2/orders/" + first).header("Authorization", otherStore))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeemsPaidPickupOnceAndUsesTheOrderLockWhenPaymentRaces() throws Exception {
        String customer = token("demo-customer");
        String staff = token("demo-bb-staff");
        String otherStore = token("demo-pj-staff");
        long product = product("Pickup tea", 1090, 1);
        String id = placeOrder(customer, product, 1, 1090);
        makeReady(id, staff);
        String pickup = pickupToken(id);

        mvc.perform(redeemRequest(1, staff, pickup)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"));
        mvc.perform(redeemRequest(2, otherStore, pickup)).andExpect(status().isNotFound());
        char last = pickup.charAt(pickup.length() - 1);
        String wrongCase = pickup.substring(0, pickup.length() - 1)
                + (Character.isUpperCase(last) ? Character.toLowerCase(last) : Character.toUpperCase(last));
        mvc.perform(redeemRequest(1, staff, wrongCase)).andExpect(status().isNotFound());

        String confirm = staffOrder(id) + "/confirm-payment";
        var responses = race(() -> mvc.perform(authenticatedPost(confirm, staff)).andReturn().getResponse(),
                () -> mvc.perform(redeemRequest(1, staff, " " + pickup + " ")).andReturn().getResponse());
        assertThat(responses.getFirst().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(responses.getFirst().getContentAsString(), "$.data.result"))
                .isEqualTo("CONFIRMED");
        var pickupResponse = responses.getLast();
        if (pickupResponse.getStatus() == 409) {
            assertThat(JsonPath.<String>read(pickupResponse.getContentAsString(), "$.code")).isEqualTo("PAYMENT_REQUIRED");
            mvc.perform(redeemRequest(1, staff, pickup)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("REDEEMED"))
                    .andExpect(jsonPath("$.data.orderId").value(id));
        } else {
            assertThat(pickupResponse.getStatus()).isEqualTo(200);
            assertThat(JsonPath.<String>read(pickupResponse.getContentAsString(), "$.data.result")).isEqualTo("REDEEMED");
        }
        mvc.perform(redeemRequest(1, staff, pickup)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALREADY_REDEEMED"))
                .andExpect(jsonPath("$.data.orderId").value(id));
        mvc.perform(authenticatedPost(confirm, staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALREADY_PAID"));
        mvc.perform(get("/api/v1/orders/" + id).header("Authorization", customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.pickupToken").doesNotHaveJsonPath());
    }

    @Test
    void sharesTheLastCupAndRecoversTheOriginalCampaignOrder() throws Exception {
        String customer = token("demo-customer");
        String staff = token("demo-bb-staff");
        long productId = jdbc.sql("SELECT id FROM product WHERE sku_code = 'roasted-oolong'")
                .query(Long.class).single();
        long campaignId = jdbc.sql("SELECT id FROM campaign WHERE code = 'demo-oolong'")
                .query(Long.class).single();
        jdbc.sql("UPDATE store_menu_item SET available_qty = 1 WHERE store_id = 1 AND product_id = :product")
                .param("product", productId).update();
        String claimId = UUID.randomUUID().toString();
        String receipt = "/api/v1/promotion-claims/" + claimId;
        var responses = race(
                () -> mvc.perform(campaignClaim(1, campaignId, claimId, customer)).andReturn().getResponse(),
                () -> mvc.perform(place(customer, UUID.randomUUID().toString(), checkoutBody(productId, 1, 1090)))
                        .andReturn().getResponse());

        assertThat(responses).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(201, 409);
        assertThat(quantity(productId)).isZero();
        if (responses.get(1).getStatus() == 201) {
            assertThat(quota(campaignId, 1)).isEqualTo(50);
            mvc.perform(get(receipt).header("Authorization", customer)).andExpect(status().isNotFound());
            String normalOrder = JsonPath.read(responses.get(1).getContentAsString(), "$.data.id");
            mvc.perform(authenticatedPost("/api/v1/orders/" + normalOrder + "/cancel", customer))
                    .andExpect(status().isOk());
            mvc.perform(campaignClaim(1, campaignId, claimId, customer)).andExpect(status().isCreated());
        }

        String orderId = JsonPath.read(mvc.perform(get(receipt).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ORDER_CREATED"))
                .andReturn().getResponse().getContentAsString(), "$.data.orderId");
        mvc.perform(campaignClaim(1, campaignId, claimId, customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId));
        mvc.perform(campaignClaim(2, campaignId, claimId, customer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(get(receipt).header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.source").value("PROMOTION"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.items[0].unitPriceCents").value(690));

        assertThat(quota(campaignId, 1)).isEqualTo(49);
        assertThat(quota(campaignId, 2)).isEqualTo(30);
        mvc.perform(authenticatedPost("/api/v1/orders/" + orderId + "/cancel", customer))
                .andExpect(status().isOk());
        assertThat(quantity(productId)).isEqualTo(1);
        assertThat(quota(campaignId, 1)).isEqualTo(49);
        mvc.perform(campaignClaim(1, campaignId, UUID.randomUUID().toString(), customer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_LIMIT_REACHED"));
        String otherClaimId = UUID.randomUUID().toString();
        var otherStoreResponses = race(
                () -> mvc.perform(campaignClaim(2, campaignId, otherClaimId, customer)).andReturn().getResponse(),
                () -> mvc.perform(campaignClaim(2, campaignId, otherClaimId, customer)).andReturn().getResponse());
        assertThat(otherStoreResponses).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(201, 200);
        assertThat(otherStoreResponses.get(0).getContentAsString())
                .isEqualTo(otherStoreResponses.get(1).getContentAsString());
        assertThat(quota(campaignId, 2)).isEqualTo(29);
    }

    private int quota(long campaignId, long storeId) {
        return jdbc.sql("SELECT quota_remaining FROM campaign_store WHERE campaign_id = :campaign AND store_id = :store")
                .param("campaign", campaignId).param("store", storeId).query(Integer.class).single();
    }

    private MockHttpServletRequestBuilder campaignClaim(long storeId, long campaignId, String claimId, String bearer) {
        return authenticatedPost("/api/v1/stores/" + storeId + "/campaigns/" + campaignId + "/claims", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"claimId":"%s"}
                        """.formatted(claimId));
    }

    private MockHttpServletRequestBuilder redeemRequest(long storeId, String bearer, String pickupToken) {
        return authenticatedPost("/api/v1/staff/stores/" + storeId + "/orders/redeem", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"pickupToken":"%s"}
                        """.formatted(pickupToken));
    }

    private void makeReady(String id, String staff) throws Exception {
        mvc.perform(authenticatedPost(staffOrder(id) + "/start", staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PREPARING"));
        mvc.perform(authenticatedPost(staffOrder(id) + "/ready", staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
    }

    private String pickupToken(String id) {
        return jdbc.sql("SELECT pickup_token FROM orders WHERE id = :id")
                .param("id", Long.parseLong(id)).query(String.class).single();
    }

    private String payment(String path, String staff) throws Exception {
        return mvc.perform(authenticatedPost(path, staff)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
