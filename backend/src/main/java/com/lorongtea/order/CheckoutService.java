package com.lorongtea.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.lorongtea.api.ApiException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class CheckoutService {
    private static final int MAX_ATTEMPTS = 3;

    private final CheckoutTransaction transaction;
    private final OrderMapper orders;

    CheckoutService(CheckoutTransaction transaction, OrderMapper orders) {
        this.transaction = transaction;
        this.orders = orders;
    }

    Placement place(long customerId, long storeId, String key, List<RequestedItem> items, long expectedTotalCents) {
        SortedMap<Long, Integer> quantities = mergeItems(items);
        String hash = fingerprint(storeId, quantities, expectedTotalCents);
        OrderRow previous = orders.findByCustomerAndKey(customerId, key);
        if (previous != null) {
            return replay(previous, hash);
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transaction.create(customerId, storeId, key, hash, quantities, expectedTotalCents);
                return new Placement(orders.findByCustomerAndKey(customerId, key), true);
            } catch (CheckoutTransaction.ClaimTaken exception) {
                // Read the winning row after the failed transaction has ended.
                OrderRow winner = orders.findByCustomerAndKey(customerId, key);
                if (winner != null) {
                    return replay(winner, hash);
                }
                break;
            } catch (PessimisticLockingFailureException exception) {
                // A new transaction uses the same key and normalized request.
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_RETRY_REQUIRED",
                "Retry the order with the same idempotency key");
    }

    private Placement replay(OrderRow previous, String hash) {
        if (!hash.equals(previous.requestHash())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "The idempotency key is already in use for a different order");
        }
        return new Placement(previous, false);
    }

    private SortedMap<Long, Integer> mergeItems(List<RequestedItem> items) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        try {
            for (RequestedItem item : items) {
                quantities.merge(item.productId(), item.quantity(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The combined quantity is too large");
        }
        return quantities;
    }

    private String fingerprint(long storeId, SortedMap<Long, Integer> items, long expectedTotalCents) {
        StringBuilder normalized = new StringBuilder().append(storeId).append(':').append(expectedTotalCents);
        items.forEach((productId, quantity) -> normalized.append(';').append(productId).append(':').append(quantity));
        try {
            byte[] bytes = normalized.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record RequestedItem(long productId, int quantity) {
    }

    record Placement(OrderRow order, boolean created) {
    }
}
