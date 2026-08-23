package com.lorongtea.order;

import java.net.URI;
import java.util.List;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class CustomerOrderController {
    private final CheckoutService checkout;
    private final OrderQueryService queries;
    private final CustomerCancellationService cancellation;

    CustomerOrderController(CheckoutService checkout, OrderQueryService queries,
            CustomerCancellationService cancellation) {
        this.checkout = checkout;
        this.queries = queries;
        this.cancellation = cancellation;
    }

    @PostMapping
    ResponseEntity<ApiResponse<OrderView>> place(@AuthenticationPrincipal AccountIdentity account,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,128}") String key,
            @Valid @RequestBody CheckoutRequest request) {
        List<CheckoutService.RequestedItem> items = request.items().stream()
                .map(item -> new CheckoutService.RequestedItem(item.productId(), item.quantity()))
                .toList();
        CheckoutService.Placement placement = checkout.place(
                account.userId(), request.storeId(), key, items, request.expectedTotalCents());
        OrderRow order = placement.order();
        var body = new ApiResponse<>(OrderView.customerSummary(order));
        if (placement.created()) {
            return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    ApiResponse<List<OrderView>> list(@AuthenticationPrincipal AccountIdentity account,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset) {
        return new ApiResponse<>(queries.customerHistory(account.userId(), limit, offset));
    }

    @GetMapping("/{orderId}")
    ApiResponse<OrderView> detail(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long orderId) {
        return new ApiResponse<>(queries.customerDetail(account.userId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    ApiResponse<OrderView> cancel(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long orderId) {
        return new ApiResponse<>(cancellation.cancel(account.userId(), orderId));
    }

    record CheckoutRequest(@NotNull @Positive Long storeId,
                           @NotEmpty List<@NotNull @Valid RequestedItem> items,
                           @NotNull @PositiveOrZero Long expectedTotalCents) {
    }

    record RequestedItem(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {
    }
}
