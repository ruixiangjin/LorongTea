package com.lorongtea.order;

import java.time.Instant;

record OrderRow(long id, long storeId, long customerId, String source, OrderStatus status, long totalCents,
                String requestHash, String pickupToken, Instant paidAt, Long paidBy,
                Instant createdAt, Instant updatedAt) {
    boolean isPaid() {
        return paidAt != null;
    }
}
