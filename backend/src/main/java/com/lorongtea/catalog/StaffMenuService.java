package com.lorongtea.catalog;

import java.util.List;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StaffMenuService {
    private final StaffStoreAccess access;
    private final MenuMapper menus;

    StaffMenuService(StaffStoreAccess access, MenuMapper menus) {
        this.access = access;
        this.menus = menus;
    }

    CatalogService.StoreView assignedStore(AccountIdentity account) {
        return CatalogService.StoreView.from(access.assignedStore(account));
    }

    @Transactional(readOnly = true)
    public List<MenuItemView> listMenu(AccountIdentity account, long storeId) {
        access.requireStore(account, storeId);
        return menus.findByStoreId(storeId).stream().map(MenuItemView::from).toList();
    }

    @Transactional
    public MenuItemView updateMenuItem(AccountIdentity account, long storeId, long productId,
            Long priceCents, Boolean listed) {
        access.requireStore(account, storeId);
        menus.updateMenuItem(storeId, productId, priceCents, listed);
        MenuMapper.MenuRow item = menus.findItem(storeId, productId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_ITEM_NOT_FOUND", "Menu item not found");
        }
        return MenuItemView.from(item);
    }

    record MenuItemView(String productId, String skuCode, String name, long priceCents,
                        boolean listed, int availableQty) {
        static MenuItemView from(MenuMapper.MenuRow row) {
            return new MenuItemView(Long.toString(row.productId()), row.skuCode(), row.name(),
                    row.priceCents(), row.listed(), row.availableQty());
        }
    }
}
