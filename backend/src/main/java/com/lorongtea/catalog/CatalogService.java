package com.lorongtea.catalog;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogService {
    private final StoreMapper stores;
    private final MenuMapper menus;

    CatalogService(StoreMapper stores, MenuMapper menus) {
        this.stores = stores;
        this.menus = menus;
    }

    List<StoreView> listStores() {
        return stores.selectList(Wrappers.<Store>lambdaQuery()
                        .eq(Store::isActive, true)
                        .orderByAsc(Store::getId))
                .stream()
                .map(store -> new StoreView(store.getId().toString(), store.getCode(), store.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuItemView> listMenu(long storeId) {
        Store store = stores.selectById(storeId);
        if (store == null || !store.isActive()) {
            throw new StoreNotFoundException();
        }

        return menus.findByStoreId(storeId).stream()
                .map(row -> new MenuItemView(Long.toString(row.productId()), row.skuCode(),
                        row.name(), row.priceCents(), row.listed()))
                .toList();
    }

    record StoreView(String id, String code, String name) {
    }

    record MenuItemView(String productId, String skuCode, String name, long priceCents, boolean listed) {
    }
}
