package com.lorongtea.order;

import org.springframework.stereotype.Component;

@Component
class CampaignOrderWriter {
    private final OrderMapper orders;

    CampaignOrderWriter(OrderMapper orders) {
        this.orders = orders;
    }

    long create(long customerId, long storeId, String claimId, OrderItem item) {
        var order = OrderDraft.promotion(customerId, storeId, claimId, item.unitPriceCents());
        orders.insertDraft(order);
        orders.insertItem(order.id(), item);
        return order.id();
    }
}
