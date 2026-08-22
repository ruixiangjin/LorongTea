package com.lorongtea.order;

import com.lorongtea.api.ApiException;
import org.springframework.http.HttpStatus;

final class OrderErrors {
    private OrderErrors() {
    }

    static OrderRow requireVisible(OrderRow order) {
        if (order == null) {
            throw notFound();
        }
        return order;
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }

    static ApiException invalidState() {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "The order cannot perform this action");
    }
}
