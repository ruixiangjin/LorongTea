package com.lorongtea.order;

interface CampaignOrderPlacement {
    // Invoked inside the transaction that deducts stock and quota.
    CampaignMapper.ReceiptRow place(long customerId, long storeId, long campaignId, String claimId, OrderItem item);
}
