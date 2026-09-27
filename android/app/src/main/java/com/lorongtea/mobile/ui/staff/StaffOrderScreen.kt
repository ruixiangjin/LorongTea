package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
import com.lorongtea.mobile.ui.orders.OrderReceipt

@Composable
internal fun StaffOrderScreen(
    store: Store,
    orderId: String,
    state: StaffOrderState,
    refresh: () -> Unit,
    advance: () -> Unit,
    confirmPayment: () -> Unit,
    openPickup: () -> Unit,
    back: () -> Unit,
) {
    var paymentDialog by rememberSaveable { mutableStateOf(false) }
    val order = state.order
    val action = when (order?.status) {
        OrderStatus.CREATED -> R.string.staff_start_order
        OrderStatus.PREPARING -> R.string.staff_mark_ready
        else -> null
    }
    val checkingPayment = state.awaitingPayment && state.needsRefresh
    val paymentAction = state.awaitingPayment || state.action == StaffOrderAction.PAYMENT
    val canRedeem = order?.status == OrderStatus.READY && order.paymentStatus == PaymentStatus.PAID
    Page(
        title = stringResource(R.string.order_number, orderId),
        back = back,
        actions = { RefreshAction(refresh, enabled = !state.busy) },
        bottomBar = {
            if (action != null || paymentAction || canRedeem) {
                BottomPanel {
                    val label = when {
                        state.action == StaffOrderAction.PAYMENT -> R.string.staff_confirming_payment
                        state.action == StaffOrderAction.ADVANCE -> R.string.staff_advancing
                        checkingPayment -> R.string.staff_payment_check
                        paymentAction -> R.string.staff_confirm_payment
                        canRedeem -> R.string.staff_pickup
                        else -> requireNotNull(action)
                    }
                    PrimaryAction(
                        stringResource(label),
                        onClick = when {
                            checkingPayment -> refresh
                            paymentAction -> ({ paymentDialog = true })
                            canRedeem -> openPickup
                            else -> advance
                        },
                        enabled = !state.busy && (checkingPayment || !state.needsRefresh),
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val error = state.error ?: state.message?.let { stringResource(it) }
            LoadNotice((state.loading && order == null) || state.updating, error, refresh)
            if (order != null) {
                Text(storeName(store), style = MaterialTheme.typography.titleLarge)
                StaffStatusLabel(order.status)
                StatusLabel(
                    stringResource(if (order.paymentStatus == PaymentStatus.PAID) R.string.payment_paid else R.string.payment_unpaid),
                    if (order.paymentStatus == PaymentStatus.PAID) StatusTone.ACTIVE else StatusTone.ATTENTION,
                )
                state.notice?.let { EmptyNotice(stringResource(it)) }
                OrderReceipt(order)
            }
        }
    }
    if (paymentDialog && state.awaitingPayment && order != null) {
        AlertDialog(
            onDismissRequest = { paymentDialog = false },
            title = { Text(stringResource(R.string.staff_payment_question)) },
            text = { Text(stringResource(R.string.staff_payment_amount, money(order.totalCents))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        paymentDialog = false
                        confirmPayment()
                    },
                    enabled = state.canConfirmPayment,
                ) { Text(stringResource(R.string.staff_confirm_payment)) }
            },
            dismissButton = {
                TextButton(onClick = { paymentDialog = false }) { Text(stringResource(R.string.staff_not_now)) }
            },
        )
    }
}
