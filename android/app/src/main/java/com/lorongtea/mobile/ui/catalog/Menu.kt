package com.lorongtea.mobile.ui.catalog

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
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
import com.lorongtea.mobile.data.CartProblem
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.CatalogRepository
import com.lorongtea.mobile.data.MenuItem
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.common.loadRemote
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.ScreenHeading
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
    store: String,
    refresh: () -> Unit,
    add: (MenuItem) -> Unit,
    cart: () -> Unit,
    changeStore: () -> Unit,
    navigation: @Composable () -> Unit = {},
) {
    val inCart = state.quantities.values.sumOf { it.toLong() }
    Page(
        title = store,
        actions = {
            RefreshAction(refresh, enabled = !state.menu.loading)
            TextButton(onClick = changeStore) { Text(stringResource(R.string.change_store_short)) }
        },
        bottomBar = {
            Column {
                BottomPanel {
                    val label = if (inCart > 0) stringResource(R.string.view_cart_count, inCart)
                        else stringResource(R.string.view_cart)
                    PrimaryAction(label, cart)
                }
                navigation()
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeading(stringResource(R.string.menu_heading), stringResource(R.string.store_pickup)) }
            if (state.menu.loading || state.menu.error != null) {
                item { LoadNotice(state.menu.loading, state.menu.error, refresh) }
            }
            state.cartError?.let { item { LoadNotice(false, it) } }
            if (state.menu.data?.isEmpty() == true) {
                item { EmptyNotice(stringResource(R.string.menu_empty)) }
            }
            items(state.menu.data.orEmpty(), key = { it.productId }) { item ->
                DrinkCard(item, state.quantities[item.productId] ?: 0) { add(item) }
            }
        }
    }
}

@Composable
private fun DrinkCard(item: MenuItem, quantity: Int, add: () -> Unit) {
    val name = drinkName(item.name)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrinkArtwork(item.name, Modifier.size(width = 100.dp, height = 122.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (item.listed) money(item.priceCents) else stringResource(R.string.menu_unavailable),
                    color = if (item.listed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (quantity > 0) {
                    Text(
                        stringResource(R.string.menu_in_cart, quantity),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (item.listed) {
                    val addLabel = stringResource(R.string.menu_add_drink, name)
                    FilledTonalButton(onClick = add, modifier = Modifier.semantics { contentDescription = addLabel }) {
                        Text(stringResource(R.string.menu_add))
                    }
                }
            }
        }
    }
}
