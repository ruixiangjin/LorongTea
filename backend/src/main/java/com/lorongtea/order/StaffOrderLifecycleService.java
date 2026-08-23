package com.lorongtea.order;

import java.security.SecureRandom;
import java.util.Base64;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StaffStoreAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class StaffOrderLifecycleService {
    private final OrderMapper orders;
    private final StaffStoreAccess access;
    private final SecureRandom random = new SecureRandom();

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView markReady(AccountIdentity account, long storeId, long orderId) {
        access.requireStore(account, storeId);
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int changed;
        try {
            changed = orders.markReady(storeId, orderId, token);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_RETRY_REQUIRED",
                    "Retry marking the order ready");
        }
        return result(storeId, orderId, changed, OrderStatus.READY);
    }

    private OrderView result(long storeId, long orderId, int changed, OrderStatus target) {
        OrderRow order = OrderErrors.requireVisible(orders.findForStore(storeId, orderId));
        if (changed == 0 && order.status() != target) {
            throw OrderErrors.invalidState();
        }
        return OrderView.staff(order, null);
    }
}
