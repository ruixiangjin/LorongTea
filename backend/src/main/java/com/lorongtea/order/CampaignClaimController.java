package com.lorongtea.order;

import java.net.URI;
import java.util.UUID;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CampaignClaimController {
    private static final String RECEIPT_PATH = "/api/v1/promotion-claims/";
    private static final String CLAIM_ID = "(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";

    private final CampaignClaimService claims;

    CampaignClaimController(CampaignClaimService claims) {
        this.claims = claims;
    }

    @PostMapping("/api/v1/stores/{storeId}/campaigns/{campaignId}/claims")
    ResponseEntity<ApiResponse<Receipt>> claim(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @PathVariable @Positive long campaignId,
            @Valid @RequestBody ClaimRequest request) {
        var placement = claims.claim(account.userId(), storeId, campaignId, canonical(request.claimId()));
        var response = new ApiResponse<>(Receipt.from(placement.receipt()));
        if (placement.created()) {
            return ResponseEntity.created(URI.create(RECEIPT_PATH + response.data().claimId())).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping(RECEIPT_PATH + "{claimId}")
    ApiResponse<Receipt> receipt(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Pattern(regexp = CLAIM_ID) String claimId) {
        return new ApiResponse<>(Receipt.from(claims.receipt(account.userId(), canonical(claimId))));
    }

    private static String canonical(String claimId) {
        return UUID.fromString(claimId).toString();
    }

    record ClaimRequest(@NotNull @Pattern(regexp = CLAIM_ID) String claimId) {
    }

    record Receipt(String claimId, String storeId, String campaignId, String status, String orderId) {
        static Receipt from(CampaignMapper.ReceiptRow row) {
            return new Receipt(row.claimId(), Long.toString(row.storeId()), Long.toString(row.campaignId()),
                    row.status(), Long.toString(row.orderId()));
        }
    }
}
