package com.lorongtea;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "LORONG_JWT_SECRET=dGhpcy1pcy1hLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXk=",
        "LORONG_DEMO_PASSWORD=integration-test-password",
        "lorong.redis.enabled=true",
        "spring.data.redis.timeout=500ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class RedisFeaturesIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.11-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    MeterRegistry metrics;

    @BeforeEach
    void resetBukitMenu() {
        jdbc.sql("""
                UPDATE store_menu_item SET price_cents = 1090
                WHERE store_id = 1 AND product_id = (SELECT id FROM product WHERE sku_code = 'roasted-oolong')
                """).update();
        redis.delete("lt:menu:v1:1");
        redis.delete("lt:menu:v1:2");
    }

    @Test
    void cachesMenusPerStoreAndUsesMysqlWhenRedisIsUnavailable() throws Exception {
        String bukit = "lt:menu:v1:1";
        String petaling = "lt:menu:v1:2";
        menuPrice(1, 1090);
        assertThat(redis.getExpire(bukit)).isBetween(1L, 75L);
        assertThat(redis.hasKey(petaling)).isFalse();

        jdbc.sql("""
                UPDATE store_menu_item SET price_cents = 1190
                WHERE store_id = 1 AND product_id = (SELECT id FROM product WHERE sku_code = 'roasted-oolong')
                """).update();
        menuPrice(1, 1090);
        menuPrice(2, 1150);
        assertThat(redis.hasKey(petaling)).isTrue();

        redis.expire(bukit, Duration.ofMillis(10));
        Thread.sleep(25);
        menuPrice(1, 1190);
        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            jdbc.sql("""
                    UPDATE store_menu_item SET price_cents = 1290
                    WHERE store_id = 1 AND product_id = (SELECT id FROM product WHERE sku_code = 'roasted-oolong')
                    """).update();
            menuPrice(1, 1290);
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    @Test
    void invalidatesOnlyAfterCommitAndToleratesFailedInvalidation() throws Exception {
        String staff = token("demo-bb-staff");
        long productId = jdbc.sql("SELECT id FROM product WHERE sku_code = 'roasted-oolong'").query(Long.class).single();
        String bukit = "lt:menu:v1:1";
        menuPrice(1, 1090);
        menuPrice(2, 1150);
        String before = redis.opsForValue().get(bukit);
        transactions.executeWithoutResult(transaction -> {
            try {
                patchPrice(staff, productId, 1390);
                assertThat(redis.opsForValue().get(bukit)).isEqualTo(before);
                transaction.setRollbackOnly();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        assertThat(redis.opsForValue().get(bukit)).isEqualTo(before);
        menuPrice(1, 1090);

        patchPrice(staff, productId, 1390);
        assertThat(redis.hasKey(bukit)).isFalse();
        assertThat(redis.hasKey("lt:menu:v1:2")).isTrue();
        menuPrice(1, 1390);

        var docker = REDIS.getDockerClient();
        var failures = metrics.counter("lorong.menu.cache.failure", "operation", "invalidate");
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            patchPrice(staff, productId, 1490);
            assertThat(failures.count()).isEqualTo(1);
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    private void patchPrice(String staff, long productId, int price) throws Exception {
        mvc.perform(patch("/api/v1/staff/stores/1/menu-items/" + productId)
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceCents\" :" + price + "}"))
                .andExpect(status().isOk());
    }

    private String token(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"integration-test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.data.token");
    }

    private void menuPrice(long storeId, int price) throws Exception {
        mvc.perform(get("/api/v1/stores/" + storeId + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.skuCode == 'roasted-oolong')].priceCents")
                        .value(org.hamcrest.Matchers.contains(price)));
    }
}
