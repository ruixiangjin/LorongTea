package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.lorongtea.mobile.data.CartLine
import com.lorongtea.mobile.data.CartProblem
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.totalCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartState(val lines: List<CartLine>? = null, val error: String? = null)

class CartViewModel(
    private val carts: CartRepository,
    private val accountId: String,
    private val storeId: String,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val state = combine(carts.observe(accountId, storeId), error) { lines, message ->
        CartState(lines, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartState())

    fun change(productId: String, delta: Int) {
        viewModelScope.launch {
            error.value = null
            try {
                carts.change(accountId, storeId, productId, delta)
            } catch (problem: CartProblem) {
                error.value = problem.message
            }
        }
    }
}

@Composable
fun CartScreen(state: CartState, change: (String, Int) -> Unit, back: () -> Unit) {
    Page("Cart", back = back) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadNotice(state.lines == null, state.error)
            val lines = state.lines ?: return@Column
            if (lines.isEmpty()) Text("Your cart is empty.")
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lines, key = { it.productId }) { line ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(line.name, style = MaterialTheme.typography.titleMedium)
                            Text("${money(line.priceCents)} each")
                            Row {
                                TextButton(onClick = { change(line.productId, -1) }) { Text("Remove one") }
                                TextButton(onClick = { change(line.productId, 1) }) { Text("Add one") }
                                Text("Qty ${line.quantity}", Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
            if (lines.isNotEmpty()) {
                Text(lines.totalCents()?.let { "Total ${money(it)}" } ?: "The cart total is too large.")
            }
        }
    }
}
