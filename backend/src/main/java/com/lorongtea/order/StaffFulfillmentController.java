package com.lorongtea.order;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/stores/{storeId}/orders")
class StaffFulfillmentController {
    private final ConfirmPaymentService payments;
    private final PickupRedemptionService pickup;

    StaffFulfillmentController(ConfirmPaymentService payments, PickupRedemptionService pickup) {
        this.payments = payments;
        this.pickup = pickup;
    }

    @PostMapping("/{orderId}/confirm-payment")
    ApiResponse<ConfirmPaymentService.PaymentConfirmation> confirmPayment(
            @AuthenticationPrincipal AccountIdentity account, @PathVariable @Positive long storeId,
            @PathVariable @Positive long orderId) {
        return new ApiResponse<>(payments.confirm(account, storeId, orderId));
    }

    @PostMapping("/redeem")
    ApiResponse<PickupRedemptionService.Redemption> redeem(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @Valid @RequestBody PickupRequest request) {
        return new ApiResponse<>(pickup.redeem(account, storeId, request.pickupToken()));
    }

    record PickupRequest(@NotNull String pickupToken) {
    }
}
