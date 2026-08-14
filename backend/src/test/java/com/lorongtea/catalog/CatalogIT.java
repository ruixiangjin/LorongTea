package com.lorongtea.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
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
}
