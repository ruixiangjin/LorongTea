package com.lorongtea.mobile

import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderDetailSource
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.ui.OrderDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {
    @Test
    fun foregroundReadsCoalesceAndStopWhenHiddenOrTerminal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = DelayedOrders()
        val viewModel = OrderDetailViewModel(source, Session("1", Role.CUSTOMER, "test"), "7")
        try {
            viewModel.resume()
            runCurrent()
            viewModel.refresh()
            runCurrent()
            assertEquals(1, source.reads)

            source.responses.send(order(OrderStatus.READY))
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, source.reads)

            viewModel.pause()
            runCurrent()
            advanceTimeBy(10_000)
            assertEquals(2, source.reads)

            viewModel.resume()
            runCurrent()
            assertEquals(3, source.reads)
            source.responses.send(order(OrderStatus.COMPLETED))
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(3, source.reads)

            viewModel.refresh()
            runCurrent()
            assertEquals(4, source.reads)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun order(status: OrderStatus) = Order("7", "1", status, PaymentStatus.PAID, 990, null, null)

    private class DelayedOrders : OrderDetailSource {
        val responses = Channel<Order>()
        var reads = 0

        override suspend fun detail(account: Session, orderId: String): Order {
            reads++
            return responses.receive()
        }

        override suspend fun cancel(account: Session, orderId: String): Order =
            error("This scenario only reads orders")
    }
}
