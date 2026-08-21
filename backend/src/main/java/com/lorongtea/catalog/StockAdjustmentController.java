package com.lorongtea.catalog;

import java.util.UUID;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StockAdjustmentController {
    private final StockAdjustmentService adjustments;

    StockAdjustmentController(StockAdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    @PostMapping("/api/v1/staff/stores/{storeId}/stock-adjustments")
    ApiResponse<StockAdjustmentService.AdjustmentView> adjust(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @Valid @RequestBody AdjustmentRequest request) {
        var change = new StockAdjustmentService.Change(request.operationId(), request.productId(),
                request.delta(), request.reason());
        return new ApiResponse<>(adjustments.adjust(account, storeId, change));
    }

    record AdjustmentRequest(@NotNull UUID operationId, @NotNull @Positive Long productId, @NotNull Integer delta,
                             @NotBlank @Size(max = 200) String reason) {
        @AssertTrue
        public boolean isNonzeroDelta() {
            return delta == null || delta != 0;
        }
    }
}
