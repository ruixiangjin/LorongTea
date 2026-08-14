package com.lorongtea.catalog;

import org.junit.jupiter.api.Test;
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
}
