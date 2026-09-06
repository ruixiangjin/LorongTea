package com.lorongtea.mobile.ui.orders

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderDetailSource
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.drinkName
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.common.display
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
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
fun OrderDetailScreen(
    state: OrderDetailState,
    stores: List<Store>,
    refresh: () -> Unit,
    cancel: () -> Unit,
    back: () -> Unit,
) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.pickup_code)
    val copyConfirmation = stringResource(R.string.pickup_copied)
    val order = state.order
    val pickup = order?.pickupToken?.takeIf {
        order.status == OrderStatus.READY && order.paymentStatus == PaymentStatus.PAID
    }
    val busy = state.loading || state.cancelling
    Page(
        title = order?.let { stringResource(R.string.order_number, it.id) } ?: stringResource(R.string.order_detail),
        back = back,
        actions = { RefreshAction(refresh, enabled = !busy) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (busy || state.error != null) {
                LoadNotice(busy, state.error, refresh)
            }
            if (order == null) return@Column

            OrderStatusCard(order, storeName(stores, order.storeId), hasCode = pickup != null)
            if (pickup != null) {
                PickupCard(pickup) {
                    val clip = ClipData.newPlainText(copyLabel, pickup)
                    if (Build.VERSION.SDK_INT >= 33) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    if (Build.VERSION.SDK_INT < 33) {
                        scope.launch { snackbar.showSnackbar(copyConfirmation) }
                    }
                }
            }
            OrderReceipt(order)
            if (order.status == OrderStatus.CREATED) {
                OutlinedButton(
                    onClick = { confirmCancel = true },
                    enabled = !state.cancelling,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cancel_order), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmCancel && order?.status == OrderStatus.CREATED) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.cancel_order_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    cancel()
                }) { Text(stringResource(R.string.cancel_order), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.keep_order)) }
            },
        )
    }
}

@Composable
private fun OrderStatusCard(order: Order, store: String, hasCode: Boolean) {
    val paid = order.paymentStatus == PaymentStatus.PAID
    val payment = when {
        paid -> R.string.payment_paid
        order.status == OrderStatus.CANCELLED -> R.string.payment_unpaid
        else -> R.string.payment_pay_at_store
    }
    val nextStep = when (order.status) {
        OrderStatus.CREATED -> R.string.order_detail_created
        OrderStatus.PREPARING -> R.string.order_detail_preparing
        OrderStatus.READY -> when {
            hasCode -> R.string.order_detail_ready_paid
            paid -> R.string.order_detail_ready_paid_waiting
            else -> R.string.order_detail_ready_unpaid
        }
        OrderStatus.COMPLETED -> R.string.order_detail_collected
        OrderStatus.CANCELLED -> null
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(store, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(order.status.label(), style = MaterialTheme.typography.headlineSmall)
            StatusLabel(
                stringResource(payment),
                when {
                    paid -> StatusTone.ACTIVE
                    order.status == OrderStatus.CANCELLED -> StatusTone.NEUTRAL
                    else -> StatusTone.ATTENTION
                },
            )
            if (nextStep != null) {
                Text(stringResource(nextStep), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PickupCard(code: String, copy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pickup_code), style = MaterialTheme.typography.titleMedium)
            Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            PrimaryAction(stringResource(R.string.copy_pickup_code), copy)
        }
    }
}

@Composable
private fun OrderReceipt(order: Order) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val items = order.items.orEmpty()
            if (items.isNotEmpty()) {
                Text(stringResource(R.string.order_items), style = MaterialTheme.typography.titleMedium)
                items.forEach { item ->
                    Column {
                        Text(stringResource(R.string.order_line, item.quantity, drinkName(item.productName)))
                        Text(
                            stringResource(R.string.cart_unit_price, money(item.unitPriceCents)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cart_total), style = MaterialTheme.typography.titleMedium)
                Text(money(order.totalCents), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
