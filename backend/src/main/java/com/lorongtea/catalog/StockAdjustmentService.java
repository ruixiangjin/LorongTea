package com.lorongtea.catalog;

import java.util.UUID;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class StockAdjustmentService {
    private final StaffStoreAccess access;
    private final StockAdjustmentMapper stock;
    private final InventoryMapper inventory;

    StockAdjustmentService(StaffStoreAccess access, StockAdjustmentMapper stock, InventoryMapper inventory) {
        this.access = access;
        this.stock = stock;
        this.inventory = inventory;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdjustmentView adjust(AccountIdentity account, long storeId, Change change) {
        access.requireStore(account, storeId);

        String operationId = change.operationId().toString();
        Integer quantityBefore = inventory.lockItemQuantity(storeId, change.productId());
        // A retry waiting for the item lock can now read the original transaction's committed audit row.
        StockAdjustmentMapper.Adjustment saved = stock.findAdjustment(storeId, operationId);
        if (saved != null) {
            return replay(account, change, saved);
        }
        if (quantityBefore == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_ITEM_NOT_FOUND", "Menu item not found");
        }
        if (inventory.adjustQuantity(storeId, change.productId(), change.delta()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_OUT_OF_RANGE", "Stock quantity would be out of range");
        }

        StockAdjustmentMapper.Adjustment adjustment = new StockAdjustmentMapper.Adjustment(
                change.productId(), account.userId(), change.delta(), change.reason(), quantityBefore + change.delta());
        try {
            stock.insertAdjustment(storeId, operationId, adjustment);
        } catch (DuplicateKeyException exception) {
            // A request for a different item may have claimed the same operation ID.
            throw operationConflict();
        }
        return AdjustmentView.from(change.operationId(), adjustment);
    }

    private AdjustmentView replay(AccountIdentity account, Change change, StockAdjustmentMapper.Adjustment saved) {
        if (saved.productId() != change.productId() || saved.operatorId() != account.userId()
                || saved.delta() != change.delta() || !saved.reason().equals(change.reason())) {
            throw operationConflict();
        }
        return AdjustmentView.from(change.operationId(), saved);
    }

    private ApiException operationConflict() {
        return new ApiException(HttpStatus.CONFLICT, "OPERATION_ID_CONFLICT",
                "Operation ID is already in use for a different adjustment");
    }

    record Change(UUID operationId, long productId, int delta, String reason) {
    }

    record AdjustmentView(UUID operationId, String productId, int delta, String reason, int availableQty) {
        static AdjustmentView from(UUID operationId, StockAdjustmentMapper.Adjustment adjustment) {
            return new AdjustmentView(operationId, Long.toString(adjustment.productId()), adjustment.delta(),
                    adjustment.reason(), adjustment.availableQtyAfter());
        }
    }
}
