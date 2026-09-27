package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.ScreenHeading
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.orders.tone
import com.lorongtea.mobile.ui.orders.label

@Composable
internal fun StaffStoreLoading(state: RemoteState<Store>, refresh: () -> Unit, signOut: () -> Unit) {
    Page(stringResource(R.string.staff_home), actions = {
        RefreshAction(refresh, enabled = !state.loading)
        TextButton(onClick = signOut) { Text(stringResource(R.string.sign_out)) }
    }) {
        Column(Modifier.padding(vertical = 16.dp)) {
            LoadNotice(state.loading, state.error, refresh)
        }
    }
}

@Composable
internal fun StaffHomeScreen(
    store: Store,
    queue: StaffQueue,
    refresh: () -> Unit,
    filter: (OrderStatus) -> Unit,
    more: () -> Unit,
    open: (String) -> Unit,
    signOut: () -> Unit,
    shortcuts: (@Composable () -> Unit)? = null,
) {
    val error = queue.error ?: if (queue.offline) stringResource(R.string.staff_read_failed) else null
    Page(stringResource(R.string.staff_order_queue), actions = {
        RefreshAction(refresh, enabled = !queue.loading)
        TextButton(onClick = signOut) { Text(stringResource(R.string.sign_out)) }
    }) {
        LazyColumn(
            contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "store") { ScreenHeading(storeName(store), stringResource(R.string.staff_assigned_store)) }
            if (shortcuts != null) item(key = "shortcuts") { shortcuts() }
            item(key = "filters") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrderStatus.entries.forEach { status ->
                        FilterChip(
                            selected = status == queue.filter,
                            onClick = { filter(status) },
                            label = { Text(status.staffLabel()) },
                        )
                    }
                }
            }
            if (queue.loading || error != null) item { LoadNotice(queue.loading, error, refresh) }
            if (queue.orders?.isEmpty() == true && !queue.loading) {
                item { EmptyNotice(stringResource(R.string.staff_queue_empty)) }
            }
            items(queue.orders.orEmpty(), key = { it.id }) { order ->
                StaffOrderCard(order) { open(order.id) }
            }
            if (queue.hasMore) item {
                TextButton(onClick = more, enabled = !queue.loading) { Text(stringResource(R.string.orders_load_more)) }
            }
        }
    }
}

@Composable
private fun StaffOrderCard(order: Order, open: () -> Unit) {
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.order_number, order.id), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StaffStatusLabel(order.status)
                Text(money(order.totalCents), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun StaffStatusLabel(status: OrderStatus) {
    StatusLabel(status.staffLabel(), status.tone())
}

@Composable
private fun OrderStatus.staffLabel(): String = when (this) {
    OrderStatus.CREATED -> stringResource(R.string.staff_queue_created)
    OrderStatus.PREPARING -> stringResource(R.string.staff_queue_preparing)
    OrderStatus.READY -> stringResource(R.string.staff_queue_ready)
    OrderStatus.COMPLETED, OrderStatus.CANCELLED -> label()
}
