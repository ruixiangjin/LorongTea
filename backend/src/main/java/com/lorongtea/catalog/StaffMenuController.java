package com.lorongtea.catalog;

import java.util.List;

import com.lorongtea.api.ApiResponse;
import com.lorongtea.auth.AccountIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
class StaffMenuController {
    private final StaffMenuService menus;

    StaffMenuController(StaffMenuService menus) {
        this.menus = menus;
    }

    @GetMapping("/store")
    ApiResponse<CatalogService.StoreView> assignedStore(@AuthenticationPrincipal AccountIdentity account) {
        return new ApiResponse<>(menus.assignedStore(account));
    }

    @GetMapping("/stores/{storeId}/menu-items")
    ApiResponse<List<StaffMenuService.MenuItemView>> listMenu(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId) {
        return new ApiResponse<>(menus.listMenu(account, storeId));
    }

    @PatchMapping("/stores/{storeId}/menu-items/{productId}")
    ApiResponse<StaffMenuService.MenuItemView> updateMenuItem(@AuthenticationPrincipal AccountIdentity account,
            @PathVariable @Positive long storeId, @PathVariable @Positive long productId,
            @Valid @RequestBody MenuItemUpdate request) {
        return new ApiResponse<>(menus.updateMenuItem(account, storeId, productId, request.priceCents(), request.listed()));
    }

    record MenuItemUpdate(@PositiveOrZero Long priceCents, Boolean listed) {
        @AssertTrue
        public boolean isChangePresent() {
            return priceCents != null || listed != null;
        }
    }
}
