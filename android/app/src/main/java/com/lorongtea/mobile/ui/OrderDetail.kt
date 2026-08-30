package com.lorongtea.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderRepository
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.Session
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrderDetailState(
    val order: Order? = null,
    val loading: Boolean = false,
    val cancelling: Boolean = false,
    val error: String? = null,
)

class OrderDetailViewModel(
    private val repository: OrderRepository,
    private val account: Session,
    private val orderId: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(OrderDetailState())
    val state = mutable.asStateFlow()
    private var loadingJob: Job? = null

    init { refresh() }

    fun refresh() {
        if (mutable.value.cancelling || loadingJob?.isActive == true) return
        loadingJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                mutable.value = OrderDetailState(order = repository.detail(account, orderId))
            } catch (error: ApiFailure) {
                mutable.value = mutable.value.copy(loading = false, error = error.display())
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(loading = false, error = "Connection unavailable. Try refreshing.")
            }
        }
    }

    fun cancel() {
        val current = mutable.value
        if (current.cancelling || current.loading || current.order?.status != OrderStatus.CREATED) return
        mutable.value = current.copy(cancelling = true, error = null)
        viewModelScope.launch {
            try {
                val cancelled = repository.cancel(account, orderId)
                mutable.value = OrderDetailState(order = cancelled.copy(items = current.order.items))
            } catch (error: ApiFailure) {
                mutable.value = current.copy(error = error.display())
            } catch (_: IOException) {
                mutable.value = current.copy(error = "Cancellation could not be confirmed. Refresh the order.")
            }
        }
    }
}

@Composable
fun OrderDetailScreen(state: OrderDetailState, refresh: () -> Unit, cancel: () -> Unit, back: () -> Unit) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val order = state.order
    Page(order?.let { "Order #${it.id}" } ?: "Order", back = back, actions = {
        TextButton(onClick = refresh, enabled = !state.loading && !state.cancelling) { Text("Refresh") }
    }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadNotice(state.loading || state.cancelling, state.error)
            if (order == null) return@Column
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.status.label(), style = MaterialTheme.typography.titleLarge)
                    Text(if (order.paymentStatus == PaymentStatus.PAID) "Paid" else "Awaiting payment at the store")
                    Text("Total ${money(order.totalCents)}")
                }
            }
            order.items.orEmpty().forEach { item ->
                Text("${item.quantity} × ${item.productName} · ${money(item.unitPriceCents)} each")
            }
            val pickup = order.pickupToken?.takeIf {
                order.status == OrderStatus.READY && order.paymentStatus == PaymentStatus.PAID
            }
            if (pickup != null) {
                Text("Pickup code", style = MaterialTheme.typography.titleMedium)
                Text(pickup, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = {
                    val clip = ClipData.newPlainText("Pickup code", pickup)
                    if (Build.VERSION.SDK_INT >= 33) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                }) { Text("Copy pickup code") }
            }
            if (order.status == OrderStatus.CREATED) {
                OutlinedButton(
                    onClick = { confirmCancel = true },
                    enabled = !state.cancelling && !state.loading,
                ) { Text("Cancel order") }
            }
        }
    }
    if (confirmCancel && order?.status == OrderStatus.CREATED) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this order?") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; cancel() }) { Text("Cancel order") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep order") } },
        )
    }
}
