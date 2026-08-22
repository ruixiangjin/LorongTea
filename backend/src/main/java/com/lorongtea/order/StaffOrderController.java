package com.lorongtea.order;

import java.util.List;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/stores/{storeId}/orders")
class StaffOrderController {
    private final OrderQueryService queries;

    StaffOrderController(OrderQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    ApiResponse<List<OrderView>> list(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset) {
        return new ApiResponse<>(queries.storeOrders(account, storeId, status, limit, offset));
    }

    @GetMapping("/{orderId}")
    ApiResponse<OrderView> detail(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @PathVariable @Positive long orderId) {
        return new ApiResponse<>(queries.storeDetail(account, storeId, orderId));
    }
}
