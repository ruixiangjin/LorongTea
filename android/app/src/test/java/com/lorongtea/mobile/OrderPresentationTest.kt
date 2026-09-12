package com.lorongtea.mobile

import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.ui.orders.availablePickupCode
import com.lorongtea.mobile.ui.orders.pickupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderPresentationTest {
    @Test
    fun pickupCodeAppearsOnlyForReadyPaidOrdersWithACode() {
        for (status in OrderStatus.entries) {
            for (payment in PaymentStatus.entries) {
                val order = Order("1", "1", status, payment, 1090, null, "CODE")
                val expected = if (status == OrderStatus.READY && payment == PaymentStatus.PAID) "CODE" else null
                assertEquals(expected, order.availablePickupCode())
            }
        }
        assertNull(Order("1", "1", OrderStatus.READY, PaymentStatus.PAID, 1090, null, " ").availablePickupCode())
    }

    @Test
    fun cancelledOrdersDoNotDisplayPickupProgress() {
        assertNull(OrderStatus.CANCELLED.pickupStep())
        assertEquals(listOf(1, 2, 3, 4), OrderStatus.entries.mapNotNull { it.pickupStep() })
    }
}
