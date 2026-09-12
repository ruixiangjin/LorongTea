package com.lorongtea.mobile.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.MenuScreen
import com.lorongtea.mobile.ui.catalog.MenuViewModel
import com.lorongtea.mobile.ui.catalog.StoresScreen
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.checkout.PendingViewModel
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.orders.OrdersScreen
import com.lorongtea.mobile.ui.orders.OrdersViewModel

@Composable
internal fun CustomerHome(
    app: LorongTeaApplication,
    account: Session,
    directory: RemoteViewModel<List<Store>>,
    openCart: (String) -> Unit,
    openOrder: (String) -> Unit,
) {
    val stores by directory.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CustomerTab.SHOP) }
    var activeStore by rememberSaveable { mutableStateOf<String?>(null) }
    var proposedStore by rememberSaveable { mutableStateOf<String?>(null) }
    var changingStore by rememberSaveable { mutableStateOf(false) }
    val screens = rememberSaveableStateHolder()
    val navigation: @Composable () -> Unit = {
        CustomerTabs(tab) {
            tab = it
            changingStore = false
            proposedStore = null
        }
    }
    val storeId = activeStore
    BackHandler(enabled = tab != CustomerTab.SHOP || changingStore) {
        tab = CustomerTab.SHOP
        changingStore = false
    }

    when {
        tab == CustomerTab.ORDERS -> screens.SaveableStateProvider("orders") {
            val orders = viewModel { OrdersViewModel(app.orders, account) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { orders.refresh() }
            val pending = viewModel { PendingViewModel(app.carts, account.userId) }
            OrdersScreen(
                state = orders.state.collectAsStateWithLifecycle().value,
                stores = stores.data.orEmpty(),
                refresh = orders::refresh,
                more = orders::more,
                open = openOrder,
                pending = pending.state.collectAsStateWithLifecycle().value.orEmpty(),
                openPending = openCart,
                bottomBar = navigation,
            )
        }
        storeId == null || changingStore -> StoresScreen(
            state = stores,
            refresh = directory::refresh,
            select = { store ->
                if (storeId == null || store.id == storeId) {
                    activeStore = store.id
                    changingStore = false
                } else {
                    proposedStore = store.id
                }
            },
            signOut = app.sessions::signOut,
            back = if (storeId != null) ({ changingStore = false }) else null,
            bottomBar = navigation,
        )
        else -> screens.SaveableStateProvider("menu/$storeId") {
            val menu = viewModel(key = "menu/$storeId") {
                MenuViewModel(app.catalog, app.carts, account.userId, storeId)
            }
            MenuScreen(
                state = menu.state.collectAsStateWithLifecycle().value,
                storeId = storeId,
                store = storeName(stores.data.orEmpty(), storeId),
                refresh = menu::refresh,
                add = menu::add,
                cart = { openCart(storeId) },
                changeStore = { changingStore = true },
                navigation = navigation,
            )
        }
    }

    proposedStore?.let { candidate ->
        AlertDialog(
            onDismissRequest = { proposedStore = null },
            title = { Text(stringResource(R.string.switch_store_title)) },
            text = { Text(stringResource(R.string.switch_store_description)) },
            confirmButton = {
                TextButton(onClick = {
                    activeStore = candidate
                    proposedStore = null
                    changingStore = false
                }) { Text(stringResource(R.string.switch_store)) }
            },
            dismissButton = {
                TextButton(onClick = { proposedStore = null }) { Text(stringResource(R.string.stay_here)) }
            },
        )
    }
}
