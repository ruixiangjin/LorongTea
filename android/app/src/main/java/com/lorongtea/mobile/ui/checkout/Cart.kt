package com.lorongtea.mobile.ui.checkout

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.lorongtea.mobile.data.CartPriceUpdate
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
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
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
    val priceUpdate: CartPriceUpdate? = null,
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
        if (lines.isNotEmpty() && lines.totalCents() != null && !current.hasUnavailableDrinks()) {
            progress.value = progress.value.copy(review = lines, notice = null)
        }
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
        val previousUpdate = progress.value.priceUpdate
        progress.value = CheckoutProgress(submitting = true, priceUpdate = previousUpdate)
        viewModelScope.launch {
            progress.value = try {
                when (val outcome = action()) {
                    is CheckoutOutcome.Confirmed -> CheckoutProgress(orderId = outcome.orderId)
                    is CheckoutOutcome.Notice -> CheckoutProgress(notice = outcome.message, priceUpdate = previousUpdate)
                    is CheckoutOutcome.Repriced -> CheckoutProgress(priceUpdate = outcome.update)
                    CheckoutOutcome.AwaitingConfirmation, CheckoutOutcome.RefreshPrices -> CheckoutProgress()
                }
            } catch (problem: CartProblem) {
                CheckoutProgress(notice = problem.message, priceUpdate = previousUpdate)
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
    val hasUnavailable = state.hasUnavailableDrinks()
    val reviewed = state.checkout.review
    BackHandler(enabled = reviewed != null, onBack = dismissReview)
    if (reviewed != null) {
        CheckoutReview(reviewed, store, confirm, dismissReview)
        return
    }
    Page(
        title = store,
        back = back,
        bottomBar = {
            if (pending != null || lines.isNotEmpty()) {
                BottomPanel {
                    if (lines.isNotEmpty() && total != null && !hasUnavailable) {
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
                        pending != null -> R.string.checkout_find_order
                        else -> R.string.review_order
                    }
                    PrimaryAction(
                        label = stringResource(label),
                        onClick = if (pending != null) recover else review,
                        enabled = !state.checkout.submitting && (pending != null || (total != null && !hasUnavailable)),
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
            val priceUpdate = state.checkout.priceUpdate
            if (hasUnavailable) {
                item { EmptyNotice(stringResource(R.string.checkout_remove_unavailable)) }
            } else if (priceUpdate != null && pending == null && lines.isNotEmpty()) {
                item {
                    PriceUpdateNotice(priceUpdate, total)
                }
            }
            if (state.lines?.isEmpty() == true && pending == null) {
                item { EmptyNotice(stringResource(R.string.cart_empty)) }
            }
            if (total == null) {
                item { LoadNotice(false, stringResource(R.string.cart_total_too_large)) }
            }
            items(lines, key = { it.productId }) { line ->
                CartItem(line, editable, line.productId in priceUpdate?.unavailableProducts.orEmpty(), change)
            }
        }
    }
}

internal fun CartState.hasUnavailableDrinks(): Boolean =
    lines.orEmpty().any { it.productId in checkout.priceUpdate?.unavailableProducts.orEmpty() }

@Composable
private fun PriceUpdateNotice(update: CartPriceUpdate, cartTotal: Long?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.checkout_prices_review), style = MaterialTheme.typography.labelLarge)
            val before = update.previousTotal
            val after = update.updatedTotal
            if (before != null && after != null && after == cartTotal && before != after &&
                update.unavailableProducts.isEmpty()
            ) {
                CheckoutAmount(stringResource(R.string.checkout_previous_total), before)
                CheckoutAmount(stringResource(R.string.checkout_updated_total), after)
            }
        }
    }
}

@Composable
private fun CheckoutReview(lines: List<CartLine>, store: String, confirm: () -> Unit, back: () -> Unit) {
    val total = lines.totalCents() ?: return
    Page(
        title = stringResource(R.string.confirm_order),
        back = back,
        bottomBar = {
            BottomPanel {
                CheckoutAmount(stringResource(R.string.cart_total), total)
                PrimaryAction(stringResource(R.string.place_order), confirm)
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeading(
                    stringResource(R.string.review_order),
                    stringResource(R.string.checkout_confirm_description),
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(R.string.checkout_store, store),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(lines, key = { it.productId }) { line ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        stringResource(R.string.order_line, line.quantity, drinkName(line.name)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.cart_unit_price, money(line.priceCents)))
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }
            item {
                EmptyNotice(stringResource(R.string.checkout_pay_later, money(total)))
            }
        }
    }
}

@Composable
private fun CheckoutAmount(label: String, total: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(money(total), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CartItem(
    line: CartLine,
    editable: Boolean,
    unavailable: Boolean,
    change: (String, Int) -> Unit,
) {
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
                if (unavailable) {
                    StatusLabel(stringResource(R.string.checkout_item_unavailable), StatusTone.ATTENTION)
                } else {
                    Text(stringResource(R.string.cart_unit_price, money(line.priceCents)))
                }
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
                            enabled = editable && !unavailable,
                            modifier = Modifier.semantics { contentDescription = addLabel },
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
    }
}
