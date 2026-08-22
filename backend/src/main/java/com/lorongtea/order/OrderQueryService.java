package com.lorongtea.order;

import java.util.List;

import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StaffStoreAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderQueryService {
    private final OrderMapper orders;
    private final StaffStoreAccess access;

    OrderQueryService(OrderMapper orders, StaffStoreAccess access) {
        this.orders = orders;
        this.access = access;
    }

    List<OrderView> customerHistory(long customerId, int limit, int offset) {
        return orders.listForCustomer(customerId, limit, offset).stream().map(OrderView::customerSummary).toList();
    }

    @Transactional(readOnly = true)
    public OrderView customerDetail(long customerId, long orderId) {
        OrderRow order = OrderErrors.requireVisible(orders.findForCustomer(customerId, orderId));
        return OrderView.customerDetail(order, orders.findItems(order.id()));
    }

    @Transactional(readOnly = true)
    public List<OrderView> storeOrders(AccountIdentity account, long storeId, OrderStatus status, int limit, int offset) {
        access.requireStore(account, storeId);
        return orders.listForStore(storeId, status, status == OrderStatus.CREATED, limit, offset).stream()
                .map(order -> OrderView.staff(order, null)).toList();
    }

    @Transactional(readOnly = true)
    public OrderView storeDetail(AccountIdentity account, long storeId, long orderId) {
        access.requireStore(account, storeId);
        OrderRow order = OrderErrors.requireVisible(orders.findForStore(storeId, orderId));
        return OrderView.staff(order, orders.findItems(order.id()));
    }
}
