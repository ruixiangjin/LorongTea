package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderRepository
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.Session
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
fun OrdersScreen(state: OrderHistory, refresh: () -> Unit, more: () -> Unit, open: (String) -> Unit, back: () -> Unit) {
    Page("My orders", back = back, actions = {
        TextButton(onClick = refresh, enabled = !state.loading) { Text("Refresh") }
    }) {
        Column {
            LoadNotice(state.loading, state.error)
            if (state.orders?.isEmpty() == true) Text("You have no orders.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.orders.orEmpty(), key = { it.id }) { order ->
                    Card(onClick = { open(order.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Order #${order.id}", style = MaterialTheme.typography.titleMedium)
                            Text(order.status.label())
                            Text(money(order.totalCents))
                        }
                    }
                }
                if (state.hasMore) item {
                    TextButton(onClick = more, enabled = !state.loading) { Text("Load more") }
                }
            }
        }
    }
}

internal fun OrderStatus.label() = when (this) {
    OrderStatus.CREATED -> "Waiting for the store"
    OrderStatus.PREPARING -> "Preparing"
    OrderStatus.READY -> "Ready for pickup"
    OrderStatus.COMPLETED -> "Collected"
    OrderStatus.CANCELLED -> "Cancelled"
}
