package com.lorongtea.mobile.ui.catalog

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
import com.lorongtea.mobile.data.CartProblem
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.CatalogRepository
import com.lorongtea.mobile.data.MenuItem
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.common.loadRemote
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MenuState(
    val menu: RemoteState<List<MenuItem>> = RemoteState(),
    val quantities: Map<String, Int> = emptyMap(),
    val cartError: String? = null,
)

class MenuViewModel(
    private val catalog: CatalogRepository,
    private val carts: CartRepository,
    private val accountId: String,
    private val storeId: String,
) : ViewModel() {
    private val menu = MutableStateFlow(RemoteState<List<MenuItem>>())
    private val cartError = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    val state = combine(menu, carts.observe(accountId, storeId), cartError) { remote, cart, error ->
        MenuState(remote, cart.associate { it.productId to it.quantity }, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuState())

    init { refresh() }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            menu.value = menu.value.copy(loading = true, error = null)
            menu.value = loadRemote(menu.value) { catalog.menu(storeId) }
        }
    }

    fun add(item: MenuItem) {
        viewModelScope.launch {
            cartError.value = null
            try {
                carts.add(accountId, storeId, item)
            } catch (problem: CartProblem) {
                cartError.value = problem.message
            }
        }
    }
}

@Composable
fun MenuScreen(
    state: MenuState,
    refresh: () -> Unit,
    add: (MenuItem) -> Unit,
    cart: () -> Unit,
    back: () -> Unit,
) {
    Page("Menu", back = back, actions = {
        TextButton(onClick = refresh, enabled = !state.menu.loading) { Text("Refresh") }
        TextButton(onClick = cart) { Text("Cart") }
    }) {
        Column {
            LoadNotice(state.menu.loading, state.cartError ?: state.menu.error)
            if (state.menu.data?.isEmpty() == true) Text("This store has an empty menu.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.menu.data.orEmpty(), key = { it.productId }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (item.listed) money(item.priceCents) else "Unavailable")
                                state.quantities[item.productId]?.let { Text("In cart: $it") }
                            }
                            if (item.listed) TextButton(onClick = { add(item) }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}
