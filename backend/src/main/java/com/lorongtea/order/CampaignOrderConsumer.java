package com.lorongtea.order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
@ConditionalOnProperty(name = "lorong.campaign.async.enabled", havingValue = "true")
class CampaignOrderConsumer {
    static final String LISTENER_ID = "campaign-order-worker";
    private static final Logger log = LoggerFactory.getLogger(CampaignOrderConsumer.class);

    private final CampaignOrderTransaction orders;
    private final MeterRegistry metrics;

    CampaignOrderConsumer(CampaignOrderTransaction orders, MeterRegistry metrics) {
        this.orders = orders;
        this.metrics = metrics;
    }

    @RabbitListener(id = LISTENER_ID, queues = CampaignBrokerConfiguration.WORK_QUEUE)
    void receive(Message message, Channel channel) throws IOException, InterruptedException {
        var properties = message.getMessageProperties();
        long tag = properties.getDeliveryTag();
        String eventId = properties.getMessageId();
        String claimId = new String(message.getBody(), StandardCharsets.US_ASCII);
        if (!isCanonicalUuid(eventId) || !isCanonicalUuid(claimId)) {
            quarantine(channel, tag, "invalid");
            return;
        }

        CampaignOrderTransaction.Outcome outcome;
        try {
            outcome = completeWithRetry(eventId, claimId);
        } catch (RuntimeException exception) {
            log.warn("Campaign event {} failed. Its reservation can expire and be released.", eventId, exception);
            quarantine(channel, tag, "error");
            return;
        }

        if (outcome == CampaignOrderTransaction.Outcome.INVALID) {
            quarantine(channel, tag, "invalid");
            return;
        }
        // The transactional service has committed before the delivery is acknowledged.
        channel.basicAck(tag, false);
        metrics.counter("lorong.campaign.consumer", "result", outcome.name().toLowerCase(Locale.ROOT)).increment();
    }

    private CampaignOrderTransaction.Outcome completeWithRetry(String eventId, String claimId)
            throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                return orders.complete(eventId, claimId);
            } catch (DataAccessException | TransactionException exception) {
                if (attempt == 2) {
                    throw exception;
                }
                Thread.sleep(50L << attempt);
            }
        }
    }

    private void quarantine(Channel channel, long tag, String result) throws IOException {
        channel.basicReject(tag, false);
        metrics.counter("lorong.campaign.consumer", "result", result).increment();
    }

    private boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
