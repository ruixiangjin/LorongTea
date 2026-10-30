package com.lorongtea.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "sync")
class SyncCampaignOrderPlacement implements CampaignOrderPlacement {
    private final CampaignMapper campaigns;
    private final CampaignOrderWriter orders;

    SyncCampaignOrderPlacement(CampaignMapper campaigns, CampaignOrderWriter orders) {
        this.campaigns = campaigns;
        this.orders = orders;
    }

    @Override
    public CampaignMapper.ReceiptRow place(long customerId, long storeId, long campaignId, String claimId, OrderItem item) {
        campaigns.insertClaim(claimId, customerId, campaignId, storeId, item);
        long orderId = orders.create(customerId, storeId, claimId, item);
        return new CampaignMapper.ReceiptRow(claimId, customerId, campaignId, storeId, "ORDER_CREATED", orderId, null);
    }
}
