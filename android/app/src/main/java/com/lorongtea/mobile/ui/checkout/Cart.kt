package com.lorongtea.mobile.ui.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.CartLine
import com.lorongtea.mobile.data.CartProblem
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.CheckoutOutcome
import com.lorongtea.mobile.data.CheckoutRepository
import com.lorongtea.mobile.data.PendingCheckout
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.totalCents
import com.lorongtea.mobile.ui.catalog.DrinkArtwork
import com.lorongtea.mobile.ui.catalog.drinkName
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.ScreenHeading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CheckoutProgress(
    val review: List<CartLine>? = null,
    val submitting: Boolean = false,
    val notice: String? = null,
    val orderId: String? = null,
)

data class CartState(
    val lines: List<CartLine>? = null,
    val pending: PendingCheckout? = null,
    val checkout: CheckoutProgress = CheckoutProgress(),
)

class CartViewModel(
    private val carts: CartRepository,
    private val checkout: CheckoutRepository,
    private val account: Session,
    private val storeId: String,
) : ViewModel() {
    private val progress = MutableStateFlow(CheckoutProgress())
    val state = combine(carts.observe(account.userId, storeId), carts.observePending(account.userId, storeId), progress) {
            lines, pending, status -> CartState(lines, pending, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartState())

    fun change(productId: String, delta: Int) {
        if (progress.value.submitting) return
        viewModelScope.launch {
            progress.value = progress.value.copy(notice = null)
            try {
                carts.change(account.userId, storeId, productId, delta)
            } catch (problem: CartProblem) {
                progress.value = progress.value.copy(notice = problem.message)
            }
        }
    }

    fun review() {
        val current = state.value
        if (current.pending != null || progress.value.submitting) return
        val lines = current.lines.orEmpty()
        if (lines.isNotEmpty() && lines.totalCents() != null) progress.value = CheckoutProgress(review = lines)
    }

    fun dismissReview() {
        progress.value = progress.value.copy(review = null)
    }

    fun confirm() {
        val lines = progress.value.review ?: return
        submit { checkout.confirm(account, storeId, lines) }
    }

    fun recover() = submit { checkout.recover(account, storeId) }

    private fun submit(action: suspend () -> CheckoutOutcome) {
        if (progress.value.submitting) return
        progress.value = CheckoutProgress(submitting = true)
        viewModelScope.launch {
            progress.value = try {
                when (val outcome = action()) {
                    is CheckoutOutcome.Confirmed -> CheckoutProgress(orderId = outcome.orderId)
                    is CheckoutOutcome.Notice -> CheckoutProgress(notice = outcome.message)
                }
            } catch (problem: CartProblem) {
                CheckoutProgress(notice = problem.message)
            }
        }
    }
}

@Composable
fun CartScreen(
    state: CartState,
    store: String,
    change: (String, Int) -> Unit,
    review: () -> Unit,
    dismissReview: () -> Unit,
    confirm: () -> Unit,
    recover: () -> Unit,
    back: () -> Unit,
) {
    val lines = state.lines.orEmpty()
    val pending = state.pending
    val total = lines.totalCents()
    val editable = pending == null && !state.checkout.submitting
    Page(
        title = store,
        back = back,
        bottomBar = {
            if (pending != null || lines.isNotEmpty()) {
                BottomPanel {
                    if (lines.isNotEmpty() && total != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.cart_total), style = MaterialTheme.typography.labelLarge)
                            Text(money(total), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    val label = when {
                        state.checkout.submitting -> R.string.order_submitting
                        pending?.priceChanged == true -> R.string.order_refresh_prices
                        pending != null -> R.string.order_retry
                        else -> R.string.review_order
                    }
                    PrimaryAction(
                        label = stringResource(label),
                        onClick = if (pending != null) recover else review,
                        enabled = !state.checkout.submitting && (pending != null || total != null),
                    )
                }
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeading(stringResource(R.string.cart), stringResource(R.string.store_pickup)) }
            if (state.lines == null || state.checkout.submitting || state.checkout.notice != null) {
                item { LoadNotice(state.lines == null || state.checkout.submitting, state.checkout.notice) }
            }
            if (pending != null) {
                item {
                    val description = if (pending.priceChanged) R.string.order_reprice_description
                        else R.string.order_retry_description
                    EmptyNotice(stringResource(description))
                }
            }
            if (state.lines?.isEmpty() == true && pending == null) {
                item { EmptyNotice(stringResource(R.string.cart_empty)) }
            }
            if (total == null) {
                item { LoadNotice(false, stringResource(R.string.cart_total_too_large)) }
            }
            items(lines, key = { it.productId }) { line ->
                CartItem(line, editable, change)
            }
        }
    }
    state.checkout.review?.totalCents()?.let { reviewedTotal ->
        AlertDialog(
            onDismissRequest = dismissReview,
            title = { Text(stringResource(R.string.confirm_order)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.checkout_store, store), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.checkout_pay_later, money(reviewedTotal)))
                }
            },
            confirmButton = { Button(onClick = confirm) { Text(stringResource(R.string.place_order)) } },
            dismissButton = { TextButton(onClick = dismissReview) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

@Composable
private fun CartItem(line: CartLine, editable: Boolean, change: (String, Int) -> Unit) {
    val name = drinkName(line.name)
    val removeLabel = stringResource(R.string.cart_remove_one, name)
    val addLabel = stringResource(R.string.cart_add_one, name)
    val quantityLabel = stringResource(R.string.cart_quantity, line.quantity)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DrinkArtwork(line.name, Modifier.size(width = 64.dp, height = 76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cart_unit_price, money(line.priceCents)))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { change(line.productId, -1) },
                            enabled = editable,
                            modifier = Modifier.semantics { contentDescription = removeLabel },
                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text(
                            line.quantity.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.semantics { contentDescription = quantityLabel },
                        )
                        IconButton(
                            onClick = { change(line.productId, 1) },
                            enabled = editable,
                            modifier = Modifier.semantics { contentDescription = addLabel },
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
    }
}
