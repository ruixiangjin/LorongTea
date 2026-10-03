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
    private final CampaignAdmission admission;

    CampaignClaimService(CampaignMapper campaigns, CampaignClaimTransaction transaction,
            CampaignAdmission admission) {
        this.campaigns = campaigns;
        this.transaction = transaction;
        this.admission = admission;
    }

    CampaignClaimTransaction.Placement claim(long customerId, long storeId, long campaignId, String claimId) {
        var replay = replay(customerId, storeId, campaignId, claimId);
        if (replay != null) {
            return replay;
        }
        try {
            admission.requirePermit(storeId, campaignId);
        } catch (ApiException rejection) {
            // An in-flight request can commit while this one waits for Redis.
            replay = replay(customerId, storeId, campaignId, claimId);
            if (replay != null) {
                return replay;
            }
            throw rejection;
        }

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

    private CampaignClaimTransaction.Placement replay(long customerId, long storeId, long campaignId, String claimId) {
        var previous = campaigns.receipt(claimId);
        return previous == null ? null : CampaignClaimTransaction.Placement.replay(previous, customerId, storeId, campaignId);
    }

    CampaignMapper.ReceiptRow receipt(long customerId, String claimId) {
        var receipt = campaigns.receipt(claimId);
        if (receipt == null || receipt.customerId() != customerId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found");
        }
        return receipt;
    }
}
