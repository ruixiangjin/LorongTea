package com.lorongtea.order;

import java.util.regex.Pattern;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StaffStoreAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PickupRedemptionService {
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final OrderMapper orders;
    private final StaffStoreAccess access;

    PickupRedemptionService(OrderMapper orders, StaffStoreAccess access) {
        this.orders = orders;
        this.access = access;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Redemption redeem(AccountIdentity account, long storeId, String input) {
        access.requireStore(account, storeId);
        String token = input.strip();
        if (!TOKEN_FORMAT.matcher(token).matches()) {
            throw OrderErrors.notFound();
        }

        // The lock and payment confirmation use the same order row to decide which request wins.
        OrderRow order = OrderErrors.requireVisible(orders.lockForPickup(storeId, token));
        String orderId = Long.toString(order.id());
        if (order.status() == OrderStatus.COMPLETED) {
            return new Redemption(Result.ALREADY_REDEEMED, orderId);
        }
        if (!order.isPaid()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_REQUIRED", "Confirm payment before redeeming pickup");
        }
        if (orders.redeem(storeId, token) != 1) {
            throw OrderErrors.invalidState();
        }
        return new Redemption(Result.REDEEMED, orderId);
    }

    enum Result {
        REDEEMED,
        ALREADY_REDEEMED
    }

    record Redemption(Result result, String orderId) {
    }
}
