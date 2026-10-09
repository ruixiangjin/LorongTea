package com.lorongtea.order;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
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
        "lorong.campaign.async.publish-interval=1h",
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
    @Autowired
    CampaignClaimTransaction claims;
    @Autowired
    CampaignMapper campaigns;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    CampaignOutboxPublisher outboxPublisher;

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
    @Test
    void reservesInventoryAndEnqueuesOnlyWhenAdmissionCommits() {
        long campaign = campaignId();
        long customer = customerId();
        long store = 1;
        String claimId = UUID.randomUUID().toString();
        Stock before = stock(campaign, store);

        transactions.executeWithoutResult(transaction -> {
            assertThat(claims.claim(customer, store, campaign, claimId).receipt().status()).isEqualTo("ACCEPTED");
            assertThat(stock(campaign, store)).isEqualTo(new Stock(before.quota() - 1, before.available() - 1));
            assertThat(eventStatus(claimId)).isEqualTo("PENDING");
            transaction.setRollbackOnly();
        });
        assertThat(campaigns.receipt(claimId)).isNull();
        assertThat(eventStatus(claimId)).isNull();
        assertThat(stock(campaign, store)).isEqualTo(before);

        var accepted = claims.claim(customer, store, campaign, claimId).receipt();
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.orderId()).isNull();
        assertThat(campaigns.receipt(claimId)).isEqualTo(accepted);
        assertThat(eventStatus(claimId)).isEqualTo("PENDING");
        assertThat(stock(campaign, store)).isEqualTo(new Stock(before.quota() - 1, before.available() - 1));
        jdbc.sql("UPDATE outbox_event SET available_at = UTC_TIMESTAMP(6) + INTERVAL 1 HOUR WHERE claim_id = :claim")
                .param("claim", claimId).update();
    }

    @Test
    void retriesAfterBrokerOutageWithTheOriginalEventId() {
        String claim = UUID.randomUUID().toString();
        claims.claim(customerId(), 1, campaignId(), claim);
        String event = eventId(claim);
        // Simulate a process exiting after the confirm, before its database update.
        publisher.publish(event, claim, CampaignBrokerConfiguration.CREATE_ROUTE);

        var docker = BROKER.getDockerClient();
        docker.pauseContainerCmd(BROKER.getContainerId()).exec();
        try {
            outboxPublisher.publishDue();
            assertThat(eventStatus(claim)).isEqualTo("PENDING");
            assertThat(jdbc.sql("""
                    SELECT COUNT(*) FROM outbox_event
                    WHERE claim_id = :claim AND attempts = 1 AND last_error IS NOT NULL
                    """).param("claim", claim).query(Integer.class).single()).isEqualTo(1);
        } finally {
            docker.unpauseContainerCmd(BROKER.getContainerId()).exec();
        }

        jdbc.sql("UPDATE outbox_event SET available_at = UTC_TIMESTAMP(6) WHERE claim_id = :claim")
                .param("claim", claim).update();
        outboxPublisher.publishDue();
        assertThat(eventStatus(claim)).isEqualTo("PUBLISHED");
        for (int i = 0; i < 2; i++) {
            var delivery = rabbit.receive(CampaignBrokerConfiguration.WORK_QUEUE, 2000);
            assertThat(delivery).isNotNull();
            assertThat(delivery.getMessageProperties().getMessageId()).isEqualTo(event);
        }
        // The broker may also have received the timed-out send after it resumed.
        rabbit.receive(CampaignBrokerConfiguration.WORK_QUEUE);
    }

    private String eventId(String claimId) {
        return jdbc.sql("SELECT event_id FROM outbox_event WHERE claim_id = :claim")
                .param("claim", claimId).query(String.class).single();
    }

    private long campaignId() {
        return jdbc.sql("SELECT id FROM campaign WHERE code = 'demo-oolong'").query(Long.class).single();
    }

    private long customerId() {
        String username = "async-" + UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app_user (username, password_hash, role)
                SELECT :username, password_hash, role FROM app_user WHERE username = 'demo-customer'
                """).param("username", username).update();
        return jdbc.sql("SELECT id FROM app_user WHERE username = :username")
                .param("username", username).query(Long.class).single();
    }

    private String eventStatus(String claimId) {
        return jdbc.sql("SELECT status FROM outbox_event WHERE claim_id = :claim")
                .param("claim", claimId).query(String.class).optional().orElse(null);
    }

    private Stock stock(long campaign, long store) {
        return jdbc.sql("""
                SELECT cs.quota_remaining, m.available_qty
                FROM campaign_store cs
                JOIN store_menu_item m ON m.store_id = cs.store_id AND m.product_id = cs.product_id
                WHERE cs.campaign_id = :campaign AND cs.store_id = :store
                """).param("campaign", campaign).param("store", store)
                .query((row, number) -> new Stock(row.getInt(1), row.getInt(2))).single();
    }

    private record Stock(int quota, int available) {
    }

}
