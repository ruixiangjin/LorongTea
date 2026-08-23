package com.lorongtea.order;

import com.lorongtea.api.ApiException;
import com.lorongtea.catalog.InventoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerCancellationService {
    private final OrderMapper orders;
    private final InventoryMapper inventory;

    CustomerCancellationService(OrderMapper orders, InventoryMapper inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView cancel(long customerId, long orderId) {
        int changed = orders.cancel(customerId, orderId);
        OrderRow order = OrderErrors.requireVisible(orders.findForCustomer(customerId, orderId));
        if (changed == 0) {
            if (order.status() != OrderStatus.CANCELLED) {
                throw OrderErrors.invalidState();
            }
            return OrderView.customerSummary(order);
        }

        for (OrderItem item : orders.findItems(orderId)) {
            if (inventory.adjustQuantity(order.storeId(), item.productId(), item.quantity()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "STOCK_OUT_OF_RANGE",
                        "Stock quantity would be out of range");
            }
        }
        return OrderView.customerSummary(order);
    }
}
