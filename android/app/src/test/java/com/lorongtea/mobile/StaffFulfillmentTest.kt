package com.lorongtea.mobile

import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderAdvance
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentConfirmation
import com.lorongtea.mobile.data.PaymentConfirmationResult
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.PickupRedemption
import com.lorongtea.mobile.data.PickupRedemptionResult
import com.lorongtea.mobile.data.StaffOrderSource
import com.lorongtea.mobile.ui.staff.StaffOrderViewModel
import com.lorongtea.mobile.ui.staff.StaffPickupViewModel
import com.lorongtea.mobile.ui.staff.StaffQueueViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffFulfillmentTest {
    @Test
    fun paymentClickIsCoalescedAndLostResponseIsReconciledAsPaid() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val submitted = CompletableDeferred<Unit>()
        var writes = 0
        var reads = 0
        val source = object : StaffOrderSource {
            override suspend fun detail(orderId: String): Order = order(paid = ++reads > 1)
            override suspend fun advance(orderId: String, action: OrderAdvance): Order = error("Unused")
            override suspend fun confirmPayment(orderId: String): PaymentConfirmation {
                writes++
                submitted.await()
                throw IOException("Response was lost")
            }
        }
        val model = StaffOrderViewModel(source, "7")
        try {
            model.refresh()
            runCurrent()
            assertTrue(model.state.value.canConfirmPayment)
            model.confirmPayment()
            model.confirmPayment()
            runCurrent()
            assertEquals(1, writes)
            submitted.complete(Unit)
            runCurrent()
            assertEquals(2, reads)
            assertEquals(PaymentStatus.PAID, model.state.value.order?.paymentStatus)
            assertEquals(R.string.staff_payment_verified, model.state.value.notice)
            model.confirmPayment()
            runCurrent()
            assertEquals(1, writes)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun paymentNeedsAReadableOrderBeforeAllowingAnotherWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var reads = 0
        var writes = 0
        val source = object : StaffOrderSource {
            override suspend fun detail(orderId: String): Order {
                if (++reads == 2) throw IOException("Still offline")
                return order(paid = false)
            }

            override suspend fun advance(orderId: String, action: OrderAdvance): Order = error("Unused")
            override suspend fun confirmPayment(orderId: String): PaymentConfirmation {
                writes++
                if (writes == 1) throw IOException("Response lost")
                return PaymentConfirmation(
                    PaymentConfirmationResult.ALREADY_PAID, orderId, OrderStatus.READY, PaymentStatus.PAID, 1090,
                )
            }
        }
        val model = StaffOrderViewModel(source, "7")
        try {
            model.refresh()
            runCurrent()
            model.confirmPayment()
            runCurrent()
            assertTrue(model.state.value.needsRefresh)
            assertFalse(model.state.value.canConfirmPayment)
            model.confirmPayment()
            runCurrent()
            assertEquals(1, writes)
            model.refresh()
            runCurrent()
            assertTrue(model.state.value.canConfirmPayment)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staffPollingStopsWhenHiddenAndRereadsOnReturn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var reads = 0
        val source = object : StaffOrderSource {
            override suspend fun detail(orderId: String): Order {
                reads++
                return order(paid = false)
            }

            override suspend fun advance(orderId: String, action: OrderAdvance): Order = error("Unused")
            override suspend fun confirmPayment(orderId: String): PaymentConfirmation = error("Unused")
        }
        val model = StaffOrderViewModel(source, "7")
        try {
            var visible = launch { model.observeWhileVisible() }
            runCurrent()
            assertEquals(1, reads)
            advanceTimeBy(5_001)
            assertEquals(2, reads)
            visible.cancel()
            advanceTimeBy(12_000)
            assertEquals(2, reads)
            visible = launch { model.observeWhileVisible() }
            runCurrent()
            assertEquals(3, reads)
            visible.cancel()
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changingQueueCancelsThePreviousFilterRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var cancelled = false
        val model = StaffQueueViewModel { status, _, _ ->
            if (status == OrderStatus.CREATED) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            listOf(order(paid = false).copy(status = status))
        }
        try {
            model.refresh()
            runCurrent()
            model.select(OrderStatus.READY)
            runCurrent()
            assertTrue(cancelled)
            assertEquals(OrderStatus.READY, model.state.value.filter)
            assertEquals(OrderStatus.READY, model.state.value.orders?.single()?.status)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun redeemValidatesLocallyAndReplaysOriginalCodeAfterTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val codes = mutableListOf<String>()
        val model = StaffPickupViewModel { code ->
            codes += code
            if (codes.size == 1) throw IOException("Response lost")
            PickupRedemption(PickupRedemptionResult.ALREADY_REDEEMED, "7")
        }
        val code = "AbCdEfGhIjKlMnOpQrSt_-"
        try {
            model.edit("short")
            model.redeem()
            runCurrent()
            assertEquals(R.string.staff_pickup_format, model.state.value.problem)
            assertTrue(codes.isEmpty())
            model.edit("  $code  ")
            model.redeem()
            model.redeem()
            runCurrent()
            assertEquals(R.string.staff_pickup_unknown, model.state.value.problem)
            model.redeem()
            runCurrent()
            assertEquals(listOf(code, code), codes)
            assertEquals(PickupRedemptionResult.ALREADY_REDEEMED, model.state.value.result?.result)
            assertTrue(model.state.value.code.isEmpty())
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun redemptionShowsPaymentRequiredAndInvalidCodeSeparately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempts = 0
        val model = StaffPickupViewModel {
            if (++attempts == 1) throw ApiFailure(409, "PAYMENT_REQUIRED", null, "Unpaid")
            throw ApiFailure(404, "ORDER_NOT_FOUND", null, "Not found")
        }
        try {
            model.edit("AbCdEfGhIjKlMnOpQrSt_-")
            model.redeem()
            runCurrent()
            assertEquals(R.string.staff_pickup_unpaid, model.state.value.problem)
            model.redeem()
            runCurrent()
            assertEquals(R.string.staff_pickup_invalid, model.state.value.problem)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun order(paid: Boolean) = Order(
        "7", "1", OrderStatus.READY, if (paid) PaymentStatus.PAID else PaymentStatus.UNPAID, 1090, null, null,
    )
}
