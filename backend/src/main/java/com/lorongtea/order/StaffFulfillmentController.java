package com.lorongtea.order;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/stores/{storeId}/orders")
class StaffFulfillmentController {
    private final ConfirmPaymentService payments;

    StaffFulfillmentController(ConfirmPaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{orderId}/confirm-payment")
    ApiResponse<ConfirmPaymentService.PaymentConfirmation> confirmPayment(
            @AuthenticationPrincipal AccountIdentity account, @PathVariable @Positive long storeId,
            @PathVariable @Positive long orderId) {
        return new ApiResponse<>(payments.confirm(account, storeId, orderId));
    }
}
