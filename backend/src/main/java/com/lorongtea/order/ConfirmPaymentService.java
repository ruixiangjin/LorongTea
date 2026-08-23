package com.lorongtea.order;

import java.time.Instant;

import com.lorongtea.auth.AccountIdentity;
import com.lorongtea.catalog.StaffStoreAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class ConfirmPaymentService {
    private static final Logger log = LoggerFactory.getLogger(ConfirmPaymentService.class);

    private final OrderMapper orders;
    private final StaffStoreAccess access;

    ConfirmPaymentService(OrderMapper orders, StaffStoreAccess access) {
        this.orders = orders;
        this.access = access;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentConfirmation confirm(AccountIdentity account, long storeId, long orderId) {
        access.requireStore(account, storeId);
        boolean confirmed = orders.confirmPayment(storeId, orderId, account.userId()) == 1;
        OrderRow order = OrderErrors.requireVisible(orders.findForStore(storeId, orderId));
        if (!order.isPaid()) {
            throw OrderErrors.invalidState();
        }
        if (confirmed) {
            logAfterCommit(order);
        }
        return new PaymentConfirmation(confirmed ? Result.CONFIRMED : Result.ALREADY_PAID,
                Long.toString(order.id()), order.status(), OrderView.PaymentStatus.from(order), order.paidAt(),
                order.paidBy().toString(), order.totalCents());
    }

    private void logAfterCommit(OrderRow order) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.atInfo()
                        .addKeyValue("orderId", Long.toString(order.id()))
                        .addKeyValue("storeId", Long.toString(order.storeId()))
                        .addKeyValue("paidBy", order.paidBy().toString())
                        .log("Order payment confirmed");
            }
        });
    }

    enum Result {
        CONFIRMED,
        ALREADY_PAID
    }

    record PaymentConfirmation(Result result, String orderId, OrderStatus status,
                               OrderView.PaymentStatus paymentStatus, Instant paidAt, String paidBy, long totalCents) {
    }
}
