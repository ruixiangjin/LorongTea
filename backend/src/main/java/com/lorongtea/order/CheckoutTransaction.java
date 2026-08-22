package com.lorongtea.order;

import java.util.SortedMap;

import com.lorongtea.api.ApiException;
import com.lorongtea.catalog.InventoryMapper;
import com.lorongtea.catalog.Store;
import com.lorongtea.catalog.StoreMapper;
import com.lorongtea.catalog.StoreNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CheckoutTransaction {
    private final OrderMapper orders;
    private final StoreMapper stores;
    private final InventoryMapper inventory;

    CheckoutTransaction(OrderMapper orders, StoreMapper stores, InventoryMapper inventory) {
        this.orders = orders;
        this.stores = stores;
        this.inventory = inventory;
    }

    @Transactional
    public long create(long customerId, long storeId, String key, String requestHash,
            SortedMap<Long, Integer> quantities, long expectedTotalCents) {
        Store store = stores.selectById(storeId);
        if (store == null || !store.isActive()) {
            throw new StoreNotFoundException();
        }

        OrderDraft draft = new OrderDraft(customerId, storeId, key, requestHash);
        try {
            orders.insertDraft(draft);
        } catch (DuplicateKeyException exception) {
            throw new ClaimTaken();
        }

        long total = 0;
        for (var line : quantities.entrySet()) {
            long productId = line.getKey();
            int quantity = line.getValue();
            InventoryMapper.CheckoutItem item = inventory.lockCheckoutItem(storeId, productId);
            if (item == null || !item.listed()) {
                throw new ApiException(HttpStatus.CONFLICT, "ITEM_UNAVAILABLE", "A menu item is unavailable");
            }
            if (item.availableQty() < quantity || inventory.deductAvailable(storeId, productId, quantity) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "Insufficient stock for a menu item");
            }
            try {
                total = Math.addExact(total, Math.multiplyExact(item.priceCents(), quantity));
            } catch (ArithmeticException exception) {
                throw priceChanged();
            }
            orders.insertItem(draft.id(), new OrderItem(productId, item.productName(), item.priceCents(), quantity));
        }

        if (total != expectedTotalCents) {
            throw priceChanged();
        }
        orders.setTotal(draft.id(), total);
        return draft.id();
    }

    private ApiException priceChanged() {
        return new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED", "The order total has changed");
    }

    static final class ClaimTaken extends RuntimeException {
    }
}
