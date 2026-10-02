package com.lorongtea.order;

import com.lorongtea.api.ApiException;
import com.lorongtea.catalog.InventoryMapper;
import com.lorongtea.catalog.StoreMapper;
import com.lorongtea.catalog.StoreNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CampaignClaimTransaction {
    private final CampaignMapper campaigns;
    private final StoreMapper stores;
    private final InventoryMapper inventory;
    private final OrderMapper orders;

    CampaignClaimTransaction(CampaignMapper campaigns, StoreMapper stores, InventoryMapper inventory, OrderMapper orders) {
        this.campaigns = campaigns;
        this.stores = stores;
        this.inventory = inventory;
        this.orders = orders;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Placement claim(long customerId, long storeId, long campaignId, String claimId) {
        var previous = campaigns.receipt(claimId);
        if (previous != null) {
            return replay(previous, customerId, storeId, campaignId);
        }

        var offer = campaigns.lockOffer(campaignId, storeId);
        // A request with the same ID may have committed while this transaction waited for the store.
        previous = campaigns.receipt(claimId);
        if (previous != null) {
            return replay(previous, customerId, storeId, campaignId);
        }
        var store = stores.selectById(storeId);
        if (store == null || !store.isActive()) {
            throw new StoreNotFoundException();
        }
        if (offer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "Campaign not found at this store");
        }

        requireOpen(campaignId);
        if (campaigns.hasClaim(campaignId, storeId, customerId)) {
            throw conflict("CAMPAIGN_LIMIT_REACHED", "You have already claimed this offer at this store");
        }
        if (offer.quotaRemaining() == 0) {
            throw soldOut();
        }
        var item = inventory.lockCheckoutItem(storeId, offer.productId());
        requireOpen(campaignId);
        if (item == null || !item.listed() || item.availableQty() == 0
                || campaigns.deductQuota(campaignId, storeId) != 1
                || inventory.deductAvailable(storeId, offer.productId(), 1) != 1) {
            throw soldOut();
        }

        campaigns.insertClaim(claimId, customerId, campaignId, storeId, offer);
        OrderDraft order = OrderDraft.promotion(customerId, storeId, claimId, offer.priceCents());
        orders.insertDraft(order);
        orders.insertItem(order.id(), new OrderItem(offer.productId(), item.productName(), offer.priceCents(), 1));
        return new Placement(new CampaignMapper.ReceiptRow(
                claimId, customerId, campaignId, storeId, "ORDER_CREATED", order.id()), true);
    }

    private Placement replay(CampaignMapper.ReceiptRow previous, long customerId, long storeId, long campaignId) {
        if (previous.customerId() != customerId || previous.storeId() != storeId || previous.campaignId() != campaignId) {
            throw conflict("IDEMPOTENCY_CONFLICT", "The claim ID is already in use for a different request");
        }
        return new Placement(previous, false);
    }

    private void requireOpen(long campaignId) {
        switch (campaigns.windowStatus(campaignId)) {
            case "UPCOMING" -> throw conflict("CAMPAIGN_NOT_STARTED", "This campaign has not opened yet");
            case "ENDED" -> throw conflict("CAMPAIGN_ENDED", "This campaign has ended");
            default -> { }
        }
    }

    private ApiException soldOut() {
        return conflict("CAMPAIGN_SOLD_OUT", "This offer is sold out at this store");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    record Placement(CampaignMapper.ReceiptRow receipt, boolean created) {
    }
}
