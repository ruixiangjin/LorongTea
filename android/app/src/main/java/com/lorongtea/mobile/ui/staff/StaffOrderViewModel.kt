package com.lorongtea.mobile.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderAdvance
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentConfirmationResult
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.StaffOrderSource
import com.lorongtea.mobile.ui.common.display
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class StaffOrderAction { ADVANCE, PAYMENT }

internal data class StaffOrderState(
    val order: Order? = null,
    val loading: Boolean = false,
    val action: StaffOrderAction? = null,
    val needsRefresh: Boolean = false,
    val message: Int? = null,
    val notice: Int? = null,
    val error: String? = null,
) {
    val updating get() = action != null
    val busy get() = loading || updating
    val awaitingPayment get() = order?.status == OrderStatus.READY && order.paymentStatus == PaymentStatus.UNPAID
    val canConfirmPayment get() = awaitingPayment && !busy && !needsRefresh
}

internal class StaffOrderViewModel(
    private val source: StaffOrderSource,
    private val orderId: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(StaffOrderState())
    val state = mutable.asStateFlow()

    fun refresh() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(loading = true, message = null, notice = null, error = null)
        viewModelScope.launch { readOrder() }
    }

    fun advance() {
        val current = mutable.value
        if (current.busy || current.needsRefresh) return
        val action = when (current.order?.status) {
            OrderStatus.CREATED -> OrderAdvance.START
            OrderStatus.PREPARING -> OrderAdvance.READY
            else -> return
        }
        update(StaffOrderAction.ADVANCE, current.order?.status) {
            val updated = source.advance(orderId, action)
            mutable.value = mutable.value.copy(order = updated.copy(items = updated.items ?: current.order?.items))
        }
    }

    fun confirmPayment() {
        val current = mutable.value
        if (!current.canConfirmPayment) return
        val order = requireNotNull(current.order)
        update(StaffOrderAction.PAYMENT, order.status) {
            val confirmation = source.confirmPayment(orderId)
            if (confirmation.orderId != orderId || confirmation.paymentStatus != PaymentStatus.PAID) {
                throw IOException("Unexpected payment confirmation")
            }
            val message = when (confirmation.result) {
                PaymentConfirmationResult.CONFIRMED -> R.string.staff_payment_confirmed
                PaymentConfirmationResult.ALREADY_PAID -> R.string.staff_payment_already
            }
            mutable.value = mutable.value.copy(
                order = order.copy(
                    status = confirmation.status,
                    paymentStatus = confirmation.paymentStatus,
                    totalCents = confirmation.totalCents,
                ),
                notice = message,
            )
            readOrder()
        }
    }

    private fun update(action: StaffOrderAction, previousStatus: OrderStatus?, send: suspend () -> Unit) {
        mutable.value = mutable.value.copy(action = action, message = null, notice = null, error = null)
        viewModelScope.launch {
            try {
                send()
            } catch (failure: ApiFailure) {
                mutable.value = mutable.value.copy(needsRefresh = true)
                if (failure.status != 401) {
                    readOrder(reconcile = action, previousStatus = previousStatus, errorAfterRead = failure.display())
                }
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(needsRefresh = true)
                readOrder(reconcile = action, previousStatus = previousStatus)
            } finally {
                mutable.value = mutable.value.copy(action = null)
            }
        }
    }

    private suspend fun readOrder(
        reconcile: StaffOrderAction? = null,
        previousStatus: OrderStatus? = null,
        errorAfterRead: String? = null,
    ) {
        try {
            val order = source.detail(orderId)
            val notice = when {
                reconcile == StaffOrderAction.PAYMENT && order.paymentStatus == PaymentStatus.PAID ->
                    R.string.staff_payment_verified
                reconcile == StaffOrderAction.PAYMENT && order.status == OrderStatus.READY && errorAfterRead == null ->
                    R.string.staff_payment_retry
                reconcile == StaffOrderAction.ADVANCE && previousStatus == order.status && errorAfterRead == null ->
                    R.string.staff_order_unchanged
                else -> mutable.value.notice
            }
            mutable.value = mutable.value.copy(
                order = order,
                needsRefresh = false,
                notice = notice,
                error = errorAfterRead.takeIf { notice == null },
            )
        } catch (failure: ApiFailure) {
            mutable.value = mutable.value.copy(error = failure.display())
        } catch (_: IOException) {
            val message = if (mutable.value.needsRefresh) R.string.staff_update_unknown else R.string.staff_read_failed
            mutable.value = mutable.value.copy(message = message)
        } finally {
            mutable.value = mutable.value.copy(loading = false)
        }
    }
}
