package com.lorongtea.order;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class CampaignReservationReaper {
    private static final Logger log = LoggerFactory.getLogger(CampaignReservationReaper.class);

    private final CampaignMapper campaigns;
    private final CampaignReservationRelease reservations;
    private final MeterRegistry metrics;

    CampaignReservationReaper(CampaignMapper campaigns, CampaignOutboxMapper outbox,
            CampaignReservationRelease reservations, MeterRegistry metrics) {
        this.campaigns = campaigns;
        this.reservations = reservations;
        this.metrics = metrics;
        Gauge.builder("lorong.campaign.async.pending", campaigns, CampaignMapper::countReserved)
                .tag("kind", "reservation").register(metrics);
        Gauge.builder("lorong.campaign.async.pending", outbox, CampaignOutboxMapper::countPending)
                .tag("kind", "outbox").register(metrics);
    }

    @Scheduled(fixedDelayString = "${lorong.campaign.async.recovery-interval:1s}",
            initialDelayString = "${lorong.campaign.async.recovery-interval:1s}")
    void releaseExpired() {
        for (var candidate : campaigns.expiredClaims()) {
            try {
                if (reservations.release(candidate)) {
                    metrics.counter("lorong.campaign.reservation.release", "result", "released").increment();
                }
            } catch (RuntimeException exception) {
                metrics.counter("lorong.campaign.reservation.release", "result", "error").increment();
                log.warn("Could not release expired campaign claim {}", candidate.claimId(), exception);
            }
        }
    }
}
