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
import com.lorongtea.mobile.data.OrderDetailSource
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.Session
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class OrderDetailState(
    val order: Order? = null,
    val loading: Boolean = false,
    val cancelling: Boolean = false,
    val error: String? = null,
)

class OrderDetailViewModel(
    private val repository: OrderDetailSource,
    private val account: Session,
    private val orderId: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(OrderDetailState())
    val state = mutable.asStateFlow()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val requestLock = Mutex()
    private var polling: Job? = null

    fun resume() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                refreshRequests.tryReceive()
                load()
                if (mutable.value.order?.status.isTerminal()) {
                    refreshRequests.receive()
                } else {
                    withTimeoutOrNull(POLL_INTERVAL_MS) { refreshRequests.receive() }
                }
            }
        }
    }

    fun pause() {
        polling?.cancel()
        polling = null
    }

    fun refresh() {
        if (!mutable.value.loading && !mutable.value.cancelling) refreshRequests.trySend(Unit)
    }

    private suspend fun load() = requestLock.withLock {
        if (mutable.value.cancelling) return@withLock
        mutable.value = mutable.value.copy(loading = true)
        try {
            val order = repository.detail(account, orderId)
            currentCoroutineContext().ensureActive()
            mutable.value = OrderDetailState(order = order)
        } catch (error: ApiFailure) {
            currentCoroutineContext().ensureActive()
            mutable.value = mutable.value.copy(error = error.display())
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            val message = if (mutable.value.order == null) "Connection unavailable. Try refreshing."
            else "Connection unavailable. Showing the last confirmed state."
            mutable.value = mutable.value.copy(error = message)
        } finally {
            mutable.value = mutable.value.copy(loading = false)
        }
    }

    fun cancel() {
        val current = mutable.value
        if (current.cancelling || current.loading || current.order?.status != OrderStatus.CREATED) return
        mutable.value = current.copy(cancelling = true, error = null)
        viewModelScope.launch {
            requestLock.withLock {
                try {
                    val cancelled = repository.cancel(account, orderId)
                    mutable.value = OrderDetailState(order = cancelled.copy(items = current.order.items))
                } catch (error: ApiFailure) {
                    mutable.value = current.copy(error = error.display())
                    refreshRequests.trySend(Unit)
                } catch (_: IOException) {
                    mutable.value = current.copy(error = "Cancellation could not be confirmed. Refresh the order.")
                    refreshRequests.trySend(Unit)
                }
            }
        }
    }

    private fun OrderStatus?.isTerminal() = this == OrderStatus.COMPLETED || this == OrderStatus.CANCELLED

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
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
                    Text(when {
                        order.paymentStatus == PaymentStatus.PAID -> "Paid"
                        order.status == OrderStatus.CANCELLED -> "Unpaid"
                        else -> "Awaiting payment at the store"
                    })
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
