package com.lorongtea.catalog;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
