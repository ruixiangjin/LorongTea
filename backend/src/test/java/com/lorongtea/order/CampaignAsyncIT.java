package com.lorongtea.order;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "LORONG_JWT_SECRET=dGhpcy1pcy1hLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXk=",
        "LORONG_DEMO_PASSWORD=integration-test-password",
        "lorong.campaign.async.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("local")
@Testcontainers
class CampaignAsyncIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb");

    @Container
    static final GenericContainer<?> BROKER = new GenericContainer<>(
            "rabbitmq:4.3.5-management-alpine@sha256:b3b8b7f95f5382a19f9ea33540e604f30aad081d37ad9aba72255135765373a1")
            .withEnv("RABBITMQ_DEFAULT_USER", "lorong_test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "integration-test-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", () -> BROKER.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "lorong_test");
        registry.add("spring.rabbitmq.password", () -> "integration-test-password");
    }

    @Autowired
    ConfirmedCampaignPublisher publisher;
    @Autowired
    RabbitTemplate rabbit;

    @Test
    void confirmsPersistentRoutingAndDetectsMandatoryReturns() {
        String event = UUID.randomUUID().toString();
        String claim = UUID.randomUUID().toString();
        publisher.publish(event, claim, CampaignBrokerConfiguration.CREATE_ROUTE);
        var delivery = rabbit.receive(CampaignBrokerConfiguration.WORK_QUEUE, 2000);
        assertThat(delivery).isNotNull();
        assertThat(delivery.getMessageProperties().getMessageId()).isEqualTo(event);
        assertThat(delivery.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(delivery.getBody(), StandardCharsets.US_ASCII)).isEqualTo(claim);
        assertThatThrownBy(() -> publisher.publish(event, claim, "unbound"))
                .isInstanceOf(AmqpException.class).hasMessageContaining("unroutable");
    }
}
