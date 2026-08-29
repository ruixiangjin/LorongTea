package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.CartLine
import com.lorongtea.mobile.data.CartProblem
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.CheckoutOutcome
import com.lorongtea.mobile.data.CheckoutRepository
import com.lorongtea.mobile.data.PendingCheckout
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.totalCents
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
    change: (String, Int) -> Unit,
    review: () -> Unit,
    dismissReview: () -> Unit,
    confirm: () -> Unit,
    recover: () -> Unit,
    back: () -> Unit,
) {
    val editable = state.pending == null && !state.checkout.submitting
    Page("Cart", back = back) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadNotice(state.lines == null || state.checkout.submitting, state.checkout.notice)
            val lines = state.lines ?: return@Column
            val total = lines.totalCents()
            if (lines.isEmpty()) Text("Your cart is empty.")
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lines, key = { it.productId }) { line ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(line.name, style = MaterialTheme.typography.titleMedium)
                            Text("${money(line.priceCents)} each")
                            Row {
                                TextButton(onClick = { change(line.productId, -1) }, enabled = editable) {
                                    Text("Remove one")
                                }
                                TextButton(onClick = { change(line.productId, 1) }, enabled = editable) {
                                    Text("Add one")
                                }
                                Text("Qty ${line.quantity}", Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
            if (lines.isNotEmpty()) {
                Text(total?.let { "Total ${money(it)}" } ?: "The cart total is too large.")
            }
            val pending = state.pending
            if (pending != null) {
                Text(if (pending.priceChanged) "Refresh prices to review your cart." else "Order awaiting confirmation.")
                Button(onClick = recover, enabled = !state.checkout.submitting) {
                    Text(if (pending.priceChanged) "Refresh prices" else "Retry order")
                }
            } else if (lines.isNotEmpty()) {
                Button(onClick = review, enabled = editable && total != null) { Text("Review order") }
            }
            state.checkout.orderId?.let { Text("Order #$it confirmed.") }
        }
    }
    state.checkout.review?.totalCents()?.let { total ->
        AlertDialog(
            onDismissRequest = dismissReview,
            title = { Text("Confirm order") },
            text = { Text("Pay ${money(total)} when you collect your order.") },
            confirmButton = { Button(onClick = confirm) { Text("Place order") } },
            dismissButton = { TextButton(onClick = dismissReview) { Text("Keep editing") } },
        )
    }
}
