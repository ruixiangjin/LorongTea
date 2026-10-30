package com.lorongtea.order;

import com.lorongtea.catalog.InventoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class CampaignReservationRelease {
    private final CampaignMapper campaigns;
    private final InventoryMapper inventory;
    private final CampaignOutboxMapper outbox;

    CampaignReservationRelease(CampaignMapper campaigns, InventoryMapper inventory, CampaignOutboxMapper outbox) {
        this.campaigns = campaigns;
        this.inventory = inventory;
        this.outbox = outbox;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean release(CampaignMapper.ExpiredClaim candidate) {
        campaigns.lockOffer(candidate.campaignId(), candidate.storeId());
        var claim = campaigns.lockClaim(candidate.claimId());
        if (claim == null || campaigns.markReleased(candidate.claimId()) == 0) {
            return false;
        }
        if (inventory.adjustQuantity(claim.storeId(), claim.productId(), 1) != 1
                || campaigns.restoreQuota(claim.campaignId(), claim.storeId()) != 1) {
            throw new IllegalStateException("Could not restore inventory and quota for an expired campaign reservation");
        }
        outbox.close(candidate.claimId());
        return true;
    }
}
