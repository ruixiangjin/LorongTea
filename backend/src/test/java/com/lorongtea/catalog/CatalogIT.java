package com.lorongtea.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "LORONG_JWT_SECRET=dGhpcy1pcy1hLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXk=",
        "LORONG_DEMO_PASSWORD=integration-test-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class CatalogIT {
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
    private StoreMapper stores;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @Transactional
    void writesAndReadsAStoreAfterMigratingAnEmptyDatabase() {
        Store store = new Store();
        store.setCode("mapper-test");
        store.setName("Mapper test store");
        store.setActive(true);

        assertThat(stores.insert(store)).isEqualTo(1);
        assertThat(store.getId()).isPositive();

        Store saved = stores.selectById(store.getId());
        assertThat(saved.getCode()).isEqualTo("mapper-test");
        assertThat(saved.getName()).isEqualTo("Mapper test store");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void listsActiveStoresWithStringIds() throws Exception {
        mvc.perform(get("/api/v1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].code").value("bukit-bintang"))
                .andExpect(jsonPath("$.data[1].id").value("2"))
                .andExpect(jsonPath("$.data[1].code").value("petaling-jaya"));
    }

    @Test
    void readsPricesAndListingStatusFromTheSelectedStore() throws Exception {
        mvc.perform(get("/api/v1/stores/1/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[1].productId").value("2"))
                .andExpect(jsonPath("$.data[1].skuCode").value("brown-sugar-boba"))
                .andExpect(jsonPath("$.data[1].priceCents").value(990))
                .andExpect(jsonPath("$.data[1].listed").value(true));

        mvc.perform(get("/api/v1/stores/2/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[1].productId").value("2"))
                .andExpect(jsonPath("$.data[1].skuCode").value("brown-sugar-boba"))
                .andExpect(jsonPath("$.data[1].priceCents").value(1050))
                .andExpect(jsonPath("$.data[1].listed").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808"})
    void rejectsInvalidStoreIds(String storeId) throws Exception {
        assertError("/api/v1/stores/" + storeId + "/menu", 400, "INVALID_REQUEST", "Invalid request");
    }

    @Test
    void returnsAConsistentErrorForAMissingStore() throws Exception {
        assertError("/api/v1/stores/999/menu", 404, "STORE_NOT_FOUND", "Store not found");
    }

    @Test
    @Transactional
    void hidesInactiveStoresFromTheCatalog() throws Exception {
        Store store = stores.selectById(2L);
        store.setActive(false);
        assertThat(stores.updateById(store)).isEqualTo(1);

        mvc.perform(get("/api/v1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        assertError("/api/v1/stores/2/menu", 404, "STORE_NOT_FOUND", "Store not found");
    }

    @Test
    void returnsTheSameEnvelopeForAnUnknownRoute() throws Exception {
        assertError("/api/v1/unknown", 404, "NOT_FOUND", "Resource not found");
    }

    @Test
    void exposesLivenessAndDatabaseReadiness() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Transactional
    void authenticatesBothRolesAndRejectsDisabledAccounts() throws Exception {
        login("demo-customer", "CUSTOMER");
        String token = login("demo-bb-staff", "STAFF");

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-bb-staff\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        jdbc.sql("UPDATE app_user SET active = FALSE WHERE username = 'demo-bb-staff'").update();
        mvc.perform(get("/api/v1/staff/store").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void enforcesRoleAndTokenValidation() throws Exception {
        String staff = login("demo-bb-staff", "STAFF");
        String customer = login("demo-customer", "CUSTOMER");
        String subject = jdbc.sql("SELECT id FROM app_user WHERE username = 'demo-bb-staff'")
                .query(String.class).single();
        Instant now = Instant.now();
        int signatureStart = staff.lastIndexOf('.') + 1;
        char replacement = staff.charAt(signatureStart) == 'a' ? 'b' : 'a';
        String tampered = staff.substring(0, signatureStart) + replacement + staff.substring(signatureStart + 1);

        mvc.perform(get("/api/v1/staff/store"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/staff/store").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        for (String token : List.of(tampered, signedToken(subject, "wrong-audience", now.plusSeconds(300)),
                signedToken(subject, "lorong-tea-api", now.minusSeconds(30)))) {
            mvc.perform(get("/api/v1/staff/store").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @Transactional
    void scopesStaffReadsAndWritesToTheAssignedStore() throws Exception {
        String staff = login("demo-bb-staff", "STAFF");
        mvc.perform(get("/api/v1/staff/store").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("bukit-bintang"));
        mvc.perform(get("/api/v1/staff/stores/1/menu-items").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].availableQty").value(80));
        mvc.perform(get("/api/v1/staff/stores/2/menu-items").header("Authorization", "Bearer " + staff))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/staff/stores/2/menu-items/2").header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/staff/stores/1/menu-items/2").header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listed").value(false));
        mvc.perform(get("/api/v1/stores/2/menu"))
                .andExpect(jsonPath("$.data[1].listed").value(false));
    }

    @Test
    @Transactional
    void updatesOnlyTheRequestedPriceAndRejectsFractionalCents() throws Exception {
        String token = "Bearer " + login("demo-bb-staff", "STAFF");
        String path = "/api/v1/staff/stores/1/menu-items/2";
        mvc.perform(patch(path).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceCents\":1249.5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceCents\":1249}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listed").value(true));
        mvc.perform(get("/api/v1/stores/1/menu"))
                .andExpect(jsonPath("$.data[1].priceCents").value(1249));
        mvc.perform(get("/api/v1/stores/2/menu"))
                .andExpect(jsonPath("$.data[1].priceCents").value(1050));
    }

    @Test
    void adjustsStockOnceForConcurrentReplaysAndRejectsInvalidChanges() throws Exception {
        String token = "Bearer " + login("demo-bb-staff", "STAFF");
        String path = "/api/v1/staff/stores/1/stock-adjustments";
        UUID operationId = UUID.randomUUID();
        String request = stockChange(operationId, 12, "Delivery");
        Callable<String> replay = () -> mvc.perform(post(path).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableQty").value(132))
                .andReturn().getResponse().getContentAsString();
        try (var requests = Executors.newFixedThreadPool(2)) {
            var first = requests.submit(replay);
            var second = requests.submit(replay);
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }

        mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(stockChange(operationId, 12, "Different reason")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_ID_CONFLICT"));
        mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(stockChange(UUID.randomUUID(), -133, "Damaged stock")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_OUT_OF_RANGE"));
        mvc.perform(post("/api/v1/staff/stores/2/stock-adjustments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNotFound());

        assertThat(jdbc.sql("SELECT available_qty FROM store_menu_item WHERE store_id = 1 AND product_id = 1")
                .query(Integer.class).single()).isEqualTo(132);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM stock_adjustment").query(Integer.class).single()).isEqualTo(1);
    }

    private String stockChange(UUID operationId, int delta, String reason) {
        return """
                {"operationId":"%s","productId":"1","delta":%d,"reason":"%s"}
                """.formatted(operationId, delta, reason);
    }

    private String login(String username, String role) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"integration-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(role))
                .andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private String signedToken(String subject, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("lorong-tea")
                .subject(subject)
                .audience(List.of(audience))
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private void assertError(String path, int httpStatus, String code, String message) throws Exception {
        mvc.perform(get(path).header("X-Request-ID", "client-supplied"))
                .andExpect(status().is(httpStatus))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(result -> {
                    String requestId = result.getResponse().getHeader("X-Request-ID");
                    assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
                    jsonPath("$.requestId").value(requestId).match(result);
                });
    }
}
