package com.lorongtea.order;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.rabbitmq.client.ConnectionFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
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
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "LORONG_JWT_SECRET=dGhpcy1pcy1hLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXk=",
        "LORONG_DEMO_PASSWORD=integration-test-password",
        "lorong.campaign.async.enabled=true",
        "lorong.campaign.async.publish-interval=1h",
        "lorong.campaign.async.recovery-interval=1h",
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
    @Autowired
    CampaignOrderTransaction campaignOrders;
    @Autowired
    RabbitListenerEndpointRegistry listeners;
    @Autowired
    MeterRegistry metrics;
    @Autowired
    CampaignReservationRelease release;

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

    @Test
    void redeliverySurvivesRollbackAndLostAcknowledgementWithoutCreatingAnotherOrder() throws Exception {
        String claim = UUID.randomUUID().toString();
        claims.claim(customerId(), 1, campaignId(), claim);
        String event = eventId(claim);
        publisher.publish(event, claim, CampaignBrokerConfiguration.CREATE_ROUTE);

        var factory = new ConnectionFactory();
        factory.setHost(BROKER.getHost());
        factory.setPort(BROKER.getMappedPort(5672));
        factory.setUsername("lorong_test");
        factory.setPassword("integration-test-password");
        try (var connection = factory.newConnection()) {
            try (var channel = connection.createChannel()) {
                assertThat(channel.basicGet(CampaignBrokerConfiguration.WORK_QUEUE, false)).isNotNull();
                transactions.executeWithoutResult(transaction -> {
                    assertThat(campaignOrders.complete(event, claim)).isEqualTo(CampaignOrderTransaction.Outcome.CREATED);
                    transaction.setRollbackOnly();
                });
                // Closing the channel leaves the delivery unacknowledged.
            }
            assertThat(campaigns.receipt(claim).status()).isEqualTo("ACCEPTED");
            try (var channel = connection.createChannel()) {
                var delivery = channel.basicGet(CampaignBrokerConfiguration.WORK_QUEUE, false);
                assertThat(delivery.getEnvelope().isRedeliver()).isTrue();
                assertThat(campaignOrders.complete(event, claim)).isEqualTo(CampaignOrderTransaction.Outcome.CREATED);
                // This time the commit succeeds and the channel closes before ACK.
            }
        }

        long originalOrder = campaigns.receipt(claim).orderId();
        jdbc.sql("UPDATE outbox_event SET available_at = UTC_TIMESTAMP(6) + INTERVAL 1 HOUR WHERE claim_id = :claim")
                .param("claim", claim).update();
        publisher.publish("invalid", claim, CampaignBrokerConfiguration.CREATE_ROUTE);
        var listener = listeners.getListenerContainer(CampaignOrderConsumer.LISTENER_ID);
        listener.start();
        try {
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertThat(
                    metrics.counter("lorong.campaign.consumer", "result", "already_final").count()).isEqualTo(1));
            var quarantined = rabbit.receive(CampaignBrokerConfiguration.DEAD_QUEUE, 4000);
            assertThat(quarantined).isNotNull();
            assertThat(quarantined.getMessageProperties().getMessageId()).isEqualTo("invalid");
            assertThat(campaigns.receipt(claim).orderId()).isEqualTo(originalOrder);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM orders WHERE promotion_claim_id = :claim")
                    .param("claim", claim).query(Integer.class).single()).isEqualTo(1);
        } finally {
            listener.stop();
        }
    }

    @Test
    void expiryRestoresExactlyOneReservationAndKeepsTheOriginalReceipt() throws Exception {
        long campaign = campaignId();
        long customer = customerId();
        long store = 1;
        Stock before = stock(campaign, store);
        String oldClaim = UUID.randomUUID().toString();
        claims.claim(customer, store, campaign, oldClaim);
        expire(oldClaim);

        var candidate = new CampaignMapper.ExpiredClaim(oldClaim, campaign, store);
        String event = eventId(oldClaim);
        try (var workers = Executors.newFixedThreadPool(3)) {
            var results = workers.invokeAll(List.<Callable<Object>>of(
                    () -> release.release(candidate),
                    () -> release.release(candidate),
                    () -> campaignOrders.complete(event, oldClaim)));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(results.get(2).get()).isIn(CampaignOrderTransaction.Outcome.EXPIRED,
                    CampaignOrderTransaction.Outcome.ALREADY_FINAL);
        }
        assertThat(stock(campaign, store)).isEqualTo(before);
        var released = claims.claim(customer, store, campaign, oldClaim).receipt();
        assertThat(released.status()).isEqualTo("FAILED_RELEASED");
        assertThat(released.failureCode()).isEqualTo("CAMPAIGN_RESERVATION_EXPIRED");
        assertThat(released.orderId()).isNull();
        assertThat(eventStatus(oldClaim)).isEqualTo("CLOSED");

        String newClaim = UUID.randomUUID().toString();
        claims.claim(customer, store, campaign, newClaim);
        assertThat(campaignOrders.complete(eventId(newClaim), newClaim)).isEqualTo(CampaignOrderTransaction.Outcome.CREATED);
        expire(newClaim);
        assertThat(release.release(new CampaignMapper.ExpiredClaim(newClaim, campaign, store))).isFalse();
        assertThat(campaigns.receipt(newClaim).orderId()).isNotNull();
        jdbc.sql("UPDATE outbox_event SET available_at = UTC_TIMESTAMP(6) + INTERVAL 1 HOUR WHERE claim_id = :claim")
                .param("claim", newClaim).update();
        assertThat(stock(campaign, store)).isEqualTo(new Stock(before.quota() - 1, before.available() - 1));
    }

    private void expire(String claim) {
        jdbc.sql("UPDATE promotion_claim SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE claim_id = :claim")
                .param("claim", claim).update();
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
