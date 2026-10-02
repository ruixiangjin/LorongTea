package com.lorongtea.mobile.ui.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import com.lorongtea.mobile.data.totalCents
import com.lorongtea.mobile.ui.campaign.CampaignCard
import com.lorongtea.mobile.ui.campaign.CampaignOffer
import com.lorongtea.mobile.ui.campaign.forStore
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.common.loadRemote
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val HOUSE_DRINK = "炭焙乌龙鲜奶"

data class MenuState(
    val menu: RemoteState<List<MenuItem>> = RemoteState(),
    val quantities: Map<String, Int> = emptyMap(),
    val cartError: String? = null,
    val cartTotal: Long? = 0L,
    val pendingCheckout: Boolean = false,
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

    val state = combine(
        menu,
        carts.observe(accountId, storeId),
        carts.observePending(accountId, storeId),
        cartError,
    ) { remote, cart, pending, error ->
        MenuState(
            menu = remote,
            quantities = cart.associate { it.productId to it.quantity },
            cartError = error,
            cartTotal = cart.totalCents(),
            pendingCheckout = pending != null,
        )
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
internal fun MenuScreen(
    state: MenuState,
    storeId: String,
    store: String,
    refresh: () -> Unit,
    add: (MenuItem) -> Unit,
    cart: () -> Unit,
    changeStore: () -> Unit,
    navigation: (@Composable () -> Unit)? = null,
    campaigns: List<CampaignOffer> = emptyList(),
    openCampaign: ((String) -> Unit)? = null,
) {
    val storeCampaigns = campaigns.mapNotNull { it.forStore(storeId) }
    val inCart = state.quantities.values.sumOf { it.toLong() }
    val items = state.menu.data.orEmpty()
    val featured = remember(items) { items.firstOrNull { it.listed && it.name == HOUSE_DRINK } }
    val menuItems = remember(items, featured) { items.filterNot { it.productId == featured?.productId } }
    Page(
        title = store,
        actions = {
            RefreshAction(refresh, enabled = !state.menu.loading)
            TextButton(onClick = changeStore) { Text(stringResource(R.string.change_store_short)) }
        },
        bottomBar = {
            Column {
                if (inCart > 0 || state.pendingCheckout) {
                    BottomPanel(padNavigationBars = navigation == null) {
                        val total = state.cartTotal
                        val label = when {
                            state.pendingCheckout -> stringResource(R.string.menu_cart_recover)
                            total != null -> stringResource(R.string.menu_cart_total, inCart, money(total))
                            else -> stringResource(R.string.view_cart_count, inCart)
                        }
                        PrimaryAction(label, cart)
                    }
                }
                navigation?.invoke()
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.menu.loading || state.menu.error != null) {
                item { LoadNotice(state.menu.loading, state.menu.error, refresh) }
            }
            state.cartError?.let { item { LoadNotice(false, it) } }
            if (state.menu.data?.isEmpty() == true) {
                item { EmptyNotice(stringResource(R.string.menu_empty)) }
            }
            if (openCampaign != null) {
                items(storeCampaigns, key = { "campaign/${it.campaignId}" }) { campaign ->
                    CampaignCard(campaign) { openCampaign(campaign.campaignId) }
                }
            }
            if (featured != null) {
                item(key = "featured") {
                    FeaturedDrink(
                        item = featured,
                        quantity = state.quantities[featured.productId] ?: 0,
                        canAdd = !state.pendingCheckout,
                        add = { add(featured) },
                    )
                }
            }
            if (menuItems.isNotEmpty()) {
                item(key = "menu_heading") {
                    Text(
                        text = stringResource(if (featured != null) R.string.menu_other_drinks else R.string.tab_shop),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
            }
            items(menuItems, key = { "drink/${it.productId}" }) { item ->
                DrinkCard(item, state.quantities[item.productId] ?: 0, !state.pendingCheckout) { add(item) }
            }
        }
    }
}

@Composable
private fun FeaturedDrink(item: MenuItem, quantity: Int, canAdd: Boolean, add: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.tea_oolong_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.7f),
            )
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.menu_featured), style = MaterialTheme.typography.labelLarge)
                Text(drinkName(item.name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.menu_featured_description), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(money(item.priceCents), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleLarge)
                    AddDrinkButton(item, quantity, canAdd, add, tonal = true)
                }
            }
        }
    }
}

@Composable
private fun DrinkCard(item: MenuItem, quantity: Int, canAdd: Boolean, add: () -> Unit) {
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
            DrinkArtwork(item.name, Modifier.size(width = 76.dp, height = 96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(drinkName(item.name), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (item.listed) money(item.priceCents) else stringResource(R.string.menu_unavailable),
                    color = if (item.listed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (item.listed) {
                    AddDrinkButton(item, quantity, canAdd, add)
                }
            }
        }
    }
}

@Composable
private fun AddDrinkButton(
    item: MenuItem,
    quantity: Int,
    enabled: Boolean,
    add: () -> Unit,
    tonal: Boolean = false,
) {
    val name = drinkName(item.name)
    val addLabel = stringResource(R.string.menu_add_drink, name)
    val description = if (quantity > 0) "$addLabel. " + stringResource(R.string.menu_in_cart, quantity)
        else addLabel
    val semantics = Modifier.semantics {
        contentDescription = description
        liveRegion = LiveRegionMode.Polite
    }
    val label: @Composable () -> Unit = {
        AnimatedContent(targetState = quantity, label = "cart quantity") { count ->
            Text(if (count > 0) "+ · $count" else stringResource(R.string.menu_add))
        }
    }
    if (tonal) FilledTonalButton(onClick = add, enabled = enabled, modifier = semantics, content = { label() })
    else Button(onClick = add, enabled = enabled, modifier = semantics, content = { label() })
}
