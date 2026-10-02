package com.lorongtea.order;

final class OrderDraft {
    private long id;
    private final long customerId;
    private final long storeId;
    private final String key;
    private final String requestHash;
    private final String source;
    private final String promotionClaimId;
    private final long totalCents;

    OrderDraft(long customerId, long storeId, String key, String requestHash) {
        this(customerId, storeId, key, requestHash, "NORMAL", null, 0);
    }

    static OrderDraft promotion(long customerId, long storeId, String claimId, long totalCents) {
        return new OrderDraft(customerId, storeId, null, null, "PROMOTION", claimId, totalCents);
    }

    private OrderDraft(long customerId, long storeId, String key, String requestHash, String source,
            String promotionClaimId, long totalCents) {
        this.customerId = customerId;
        this.storeId = storeId;
        this.key = key;
        this.requestHash = requestHash;
        this.source = source;
        this.promotionClaimId = promotionClaimId;
        this.totalCents = totalCents;
    }

    long id() {
        return id;
    }
}
