package com.lorongtea.order;

import com.lorongtea.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class CampaignClaimService {
    private final CampaignMapper campaigns;
    private final CampaignClaimTransaction transaction;

    CampaignClaimService(CampaignMapper campaigns, CampaignClaimTransaction transaction) {
        this.campaigns = campaigns;
        this.transaction = transaction;
    }

    CampaignClaimTransaction.Placement claim(long customerId, long storeId, long campaignId, String claimId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction.claim(customerId, storeId, campaignId, claimId);
            } catch (DuplicateKeyException | PessimisticLockingFailureException exception) {
                // A new transaction can see a concurrent winner after the rollback.
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAMPAIGN_RETRY_REQUIRED",
                "Retry with the same claim ID");
    }

    CampaignMapper.ReceiptRow receipt(long customerId, String claimId) {
        var receipt = campaigns.receipt(claimId);
        if (receipt == null || receipt.customerId() != customerId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found");
        }
        return receipt;
    }
}
