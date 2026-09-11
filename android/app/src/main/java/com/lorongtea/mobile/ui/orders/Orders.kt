package com.lorongtea.mobile.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderRepository
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.common.display
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrderHistory(
    val orders: List<Order>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

class OrdersViewModel(private val repository: OrderRepository, private val account: Session) : ViewModel() {
    private val mutable = MutableStateFlow(OrderHistory())
    val state = mutable.asStateFlow()
    private var nextOffset = 0

    init { refresh() }

    fun refresh() = load(reset = true)
    fun more() {
        if (mutable.value.hasMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val current = mutable.value
        if (current.loading) return
        mutable.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            val offset = if (reset) 0 else nextOffset
            try {
                val page = repository.history(account, PAGE_SIZE, offset)
                nextOffset = offset + page.size
                val list = if (reset) page else (current.orders.orEmpty() + page).distinctBy { it.id }
                mutable.value = OrderHistory(orders = list, hasMore = page.size == PAGE_SIZE)
            } catch (error: ApiFailure) {
                mutable.value = current.copy(loading = false, error = error.display())
            } catch (_: IOException) {
                mutable.value = current.copy(loading = false, error = "Connection unavailable. Try refreshing.")
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

@Composable
fun OrdersScreen(
    state: OrderHistory,
    stores: List<Store>,
    refresh: () -> Unit,
    more: () -> Unit,
    open: (String) -> Unit,
    openPending: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Page(stringResource(R.string.my_orders), bottomBar = bottomBar, actions = {
        RefreshAction(refresh, enabled = !state.loading)
    }) {
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextButton(onClick = openPending) { Text(stringResource(R.string.pending_orders)) }
            }
            if (state.loading || state.error != null) {
                item { LoadNotice(state.loading, state.error, refresh) }
            }
            if (state.orders?.isEmpty() == true) {
                item { EmptyNotice(stringResource(R.string.orders_empty)) }
            }
            items(state.orders.orEmpty(), key = { it.id }) { order ->
                Surface(
                    onClick = { open(order.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(storeName(stores, order.storeId), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.order_number, order.id),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusLabel(order.status.label(), order.status.tone())
                            Text(money(order.totalCents), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (state.hasMore) {
                item {
                    TextButton(onClick = more, enabled = !state.loading) {
                        Text(stringResource(R.string.orders_load_more))
                    }
                }
            }
        }
    }
}

@Composable
internal fun OrderStatus.label() = stringResource(
    when (this) {
        OrderStatus.CREATED -> R.string.order_created
        OrderStatus.PREPARING -> R.string.order_preparing
        OrderStatus.READY -> R.string.order_ready
        OrderStatus.COMPLETED -> R.string.order_collected
        OrderStatus.CANCELLED -> R.string.order_cancelled
    },
)

internal fun OrderStatus.tone(): StatusTone = when (this) {
    OrderStatus.CREATED -> StatusTone.ATTENTION
    OrderStatus.PREPARING, OrderStatus.READY -> StatusTone.ACTIVE
    OrderStatus.COMPLETED, OrderStatus.CANCELLED -> StatusTone.NEUTRAL
}
