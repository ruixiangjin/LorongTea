package com.lorongtea.order;

final class OrderDraft {
    private long id;
    private final long customerId;
    private final long storeId;
    private final String key;
    private final String requestHash;

    OrderDraft(long customerId, long storeId, String key, String requestHash) {
        this.customerId = customerId;
        this.storeId = storeId;
        this.key = key;
        this.requestHash = requestHash;
    }

    long id() {
        return id;
    }
}
