package com.lorongtea.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class CampaignOrderTransaction {
    private final CampaignMapper campaigns;
    private final CampaignOutboxMapper outbox;
    private final CampaignOrderWriter orders;

    CampaignOrderTransaction(CampaignMapper campaigns, CampaignOutboxMapper outbox, CampaignOrderWriter orders) {
        this.campaigns = campaigns;
        this.outbox = outbox;
        this.orders = orders;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Outcome complete(String eventId, String claimId) {
        var claim = campaigns.lockClaim(claimId);
        if (claim == null || !eventId.equals(outbox.eventId(claimId))) {
            return Outcome.INVALID;
        }
        if (!"ACCEPTED".equals(claim.status())) {
            return Outcome.ALREADY_FINAL;
        }
        if (campaigns.hasExpired(claimId)) {
            return Outcome.EXPIRED;
        }

        orders.create(claim.customerId(), claim.storeId(), claimId,
                new OrderItem(claim.productId(), claim.productName(), claim.priceCents(), 1));
        campaigns.markOrderCreated(claimId);
        return Outcome.CREATED;
    }

    enum Outcome { CREATED, ALREADY_FINAL, EXPIRED, INVALID }
}
