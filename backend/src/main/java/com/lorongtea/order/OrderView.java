package com.lorongtea.order;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

record OrderView(String id, String storeId, String source, OrderStatus status, PaymentStatus paymentStatus,
                 Instant paidAt, long totalCents, Instant createdAt, Instant updatedAt,
                 @JsonInclude(JsonInclude.Include.NON_NULL) String paidBy,
                 @JsonInclude(JsonInclude.Include.NON_NULL) String pickupToken,
                 @JsonInclude(JsonInclude.Include.NON_NULL) List<ItemView> items) {
    static OrderView customerSummary(OrderRow order) {
        return from(order, null, null, null);
    }

    static OrderView customerDetail(OrderRow order, List<OrderItem> items) {
        String pickupToken = order.status() == OrderStatus.READY && order.isPaid() ? order.pickupToken() : null;
        return from(order, null, pickupToken, items);
    }

    static OrderView staff(OrderRow order, List<OrderItem> items) {
        String paidBy = order.paidBy() == null ? null : order.paidBy().toString();
        return from(order, paidBy, null, items);
    }

    private static OrderView from(OrderRow order, String paidBy, String pickupToken, List<OrderItem> items) {
        return new OrderView(Long.toString(order.id()), Long.toString(order.storeId()), order.source(),
                order.status(), PaymentStatus.from(order), order.paidAt(), order.totalCents(),
                order.createdAt(), order.updatedAt(), paidBy, pickupToken,
                items == null ? null : items.stream().map(ItemView::from).toList());
    }

    enum PaymentStatus {
        UNPAID,
        PAID;

        static PaymentStatus from(OrderRow order) {
            return order.isPaid() ? PAID : UNPAID;
        }
    }

    record ItemView(String productId, String productName, long unitPriceCents, int quantity) {
        static ItemView from(OrderItem item) {
            return new ItemView(Long.toString(item.productId()), item.productName(), item.unitPriceCents(),
                    item.quantity());
        }
    }
}
