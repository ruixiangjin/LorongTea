package com.lorongtea.order;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class CampaignOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(CampaignOutboxPublisher.class);

    private final CampaignOutboxMapper outbox;
    private final ConfirmedCampaignPublisher broker;
    private final MeterRegistry metrics;

    CampaignOutboxPublisher(CampaignOutboxMapper outbox, ConfirmedCampaignPublisher broker, MeterRegistry metrics) {
        this.outbox = outbox;
        this.broker = broker;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${lorong.campaign.async.publish-interval:500ms}",
            initialDelayString = "${lorong.campaign.async.publish-interval:500ms}")
    void publishDue() {
        for (var event : outbox.dueEvents()) {
            try {
                broker.publish(event.eventId(), event.claimId(), CampaignBrokerConfiguration.CREATE_ROUTE);
            } catch (AmqpException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                int backoff = Math.min(30, 1 << Math.min(5, event.attempts()));
                outbox.defer(event.eventId(), backoff, exception.getClass().getSimpleName());
                metrics.counter("lorong.campaign.outbox.publish", "result", "retry").increment();
                log.debug("Campaign event {} deferred for {} seconds", event.eventId(), backoff, exception);
                continue;
            }
            // A failed database update leaves the same event ID available for the next attempt.
            if (outbox.markPublished(event.eventId()) == 1) {
                metrics.counter("lorong.campaign.outbox.publish", "result", "confirmed").increment();
            }
        }
    }
}
