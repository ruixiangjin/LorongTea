package com.lorongtea.order;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class AsyncCampaignOrderPlacement implements CampaignOrderPlacement {
    private final CampaignMapper campaigns;
    private final CampaignOutboxMapper outbox;
    private final int deadline;

    AsyncCampaignOrderPlacement(CampaignMapper campaigns, CampaignOutboxMapper outbox,
            @Value("${lorong.campaign.async.deadline-seconds}") int deadline) {
        if (deadline < 1) {
            throw new IllegalArgumentException("Campaign reservation deadline must be positive");
        }
        this.campaigns = campaigns;
        this.outbox = outbox;
        this.deadline = deadline;
    }

    @Override
    public CampaignMapper.ReceiptRow place(long customerId, long storeId, long campaignId, String claimId, OrderItem item) {
        campaigns.reserveClaim(claimId, customerId, campaignId, storeId, item, deadline);
        outbox.enqueue(UUID.randomUUID().toString(), claimId);
        return new CampaignMapper.ReceiptRow(claimId, customerId, campaignId, storeId, "ACCEPTED", null, null);
    }
}
