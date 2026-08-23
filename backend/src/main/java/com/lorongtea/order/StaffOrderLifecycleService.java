package com.lorongtea.order;

import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StaffStoreAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class StaffOrderLifecycleService {
    private final OrderMapper orders;
    private final StaffStoreAccess access;

    StaffOrderLifecycleService(OrderMapper orders, StaffStoreAccess access) {
        this.orders = orders;
        this.access = access;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView start(AccountIdentity account, long storeId, long orderId) {
        access.requireStore(account, storeId);
        int changed = orders.start(storeId, orderId);
        return result(storeId, orderId, changed, OrderStatus.PREPARING);
    }

    private OrderView result(long storeId, long orderId, int changed, OrderStatus target) {
        OrderRow order = OrderErrors.requireVisible(orders.findForStore(storeId, orderId));
        if (changed == 0 && order.status() != target) {
            throw OrderErrors.invalidState();
        }
        return OrderView.staff(order, null);
    }
}
