package com.lorongtea.order;

import java.time.Instant;
import java.util.List;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StoreMapper;
import com.lorongtea.catalog.StoreNotFoundException;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CampaignController {
    private final CampaignMapper campaigns;
    private final StoreMapper stores;

    CampaignController(CampaignMapper campaigns, StoreMapper stores) {
        this.campaigns = campaigns;
        this.stores = stores;
    }

    @GetMapping("/api/v1/stores/{storeId}/campaigns")
    ApiResponse<List<Offer>> offers(@PathVariable @Positive long storeId,
            @AuthenticationPrincipal AccountIdentity account) {
        var store = stores.selectById(storeId);
        if (store == null || !store.isActive()) {
            throw new StoreNotFoundException();
        }
        Long customerId = account != null && account.role() == AccountIdentity.Role.CUSTOMER
                ? account.userId() : null;
        return new ApiResponse<>(campaigns.offers(storeId, customerId).stream().map(Offer::from).toList());
    }

    record Offer(String campaignId, String storeId, String drinkName, long priceCents, Instant startsAt,
                 Instant endsAt, String availability, int limitPerCustomer) {
        static Offer from(CampaignMapper.OfferRow row) {
            return new Offer(Long.toString(row.campaignId()), Long.toString(row.storeId()), row.drinkName(),
                    row.priceCents(), row.startsAt(), row.endsAt(), row.availability(), 1);
        }
    }
}
