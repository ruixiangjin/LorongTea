package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.orders.OrderReceipt
import com.lorongtea.mobile.ui.orders.OrderStatusLabel

@Composable
internal fun StaffOrderScreen(
    store: Store,
    orderId: String,
    state: StaffOrderState,
    refresh: () -> Unit,
    advance: () -> Unit,
    back: () -> Unit,
) {
    val order = state.order
    val action = when (order?.status) {
        OrderStatus.CREATED -> R.string.staff_start_order
        OrderStatus.PREPARING -> R.string.staff_mark_ready
        else -> null
    }
    Page(
        title = stringResource(R.string.order_number, orderId),
        back = back,
        actions = { RefreshAction(refresh, enabled = !state.busy) },
        bottomBar = {
            if (action != null) {
                BottomPanel {
                    PrimaryAction(
                        stringResource(if (state.updating) R.string.staff_advancing else action),
                        advance,
                        enabled = !state.busy && !state.needsRefresh,
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
                OrderStatusLabel(order.status)
                OrderReceipt(order)
            }
        }
    }
}
