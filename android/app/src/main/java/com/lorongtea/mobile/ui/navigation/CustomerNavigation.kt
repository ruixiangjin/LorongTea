package com.lorongtea.mobile.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.SessionState
import com.lorongtea.mobile.ui.catalog.MenuScreen
import com.lorongtea.mobile.ui.catalog.MenuViewModel
import com.lorongtea.mobile.ui.catalog.StoresScreen
import com.lorongtea.mobile.ui.checkout.CartScreen
import com.lorongtea.mobile.ui.checkout.CartViewModel
import com.lorongtea.mobile.ui.checkout.PendingScreen
import com.lorongtea.mobile.ui.checkout.PendingViewModel
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.orders.OrderDetailScreen
import com.lorongtea.mobile.ui.orders.OrderDetailViewModel
import com.lorongtea.mobile.ui.orders.OrdersScreen
import com.lorongtea.mobile.ui.orders.OrdersViewModel
import kotlinx.coroutines.awaitCancellation

internal fun NavGraphBuilder.customerNavigation(
    app: LorongTeaApplication,
    nav: NavHostController,
    sessionState: State<SessionState>,
) {
    val session by sessionState
    navigation(startDestination = "stores", route = CUSTOMER) {
        composable("stores") {
            if (session.account?.role != Role.CUSTOMER) return@composable
            val stores = viewModel { RemoteViewModel(app.catalog::stores) }
            var lastStore by rememberSaveable { mutableStateOf<String?>(null) }
            var proposedStore by rememberSaveable { mutableStateOf<String?>(null) }
            fun openStore(storeId: String) {
                lastStore = storeId
                proposedStore = null
                nav.navigate("menu/$storeId")
            }
            StoresScreen(stores.state.collectAsStateWithLifecycle().value, stores::refresh, select = { store ->
                if (lastStore != null && lastStore != store.id) proposedStore = store.id else openStore(store.id)
            }, actions = {
                TextButton(onClick = app.sessions::signOut) { Text("Sign out") }
            }, shortcuts = {
                TextButton(onClick = { nav.navigate("orders") }) { Text("My orders") }
                TextButton(onClick = { nav.navigate("pending") }) { Text("Pending orders") }
            })
            proposedStore?.let { storeId ->
                AlertDialog(
                    onDismissRequest = { proposedStore = null },
                    title = { Text("Switch stores?") },
                    text = { Text("Each store has its own menu, prices and cart.") },
                    confirmButton = { TextButton(onClick = { openStore(storeId) }) { Text("Switch store") } },
                    dismissButton = { TextButton(onClick = { proposedStore = null }) { Text("Stay here") } },
                )
            }
        }
        composable("menu/{storeId}") { entry ->
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            val storeId = requireNotNull(entry.arguments?.getString("storeId"))
            val menu = viewModel { MenuViewModel(app.catalog, app.carts, account.userId, storeId) }
            MenuScreen(
                menu.state.collectAsStateWithLifecycle().value,
                menu::refresh,
                menu::add,
                cart = { nav.navigate("cart/$storeId") },
                back = { nav.popBackStack() },
            )
        }
        composable("cart/{storeId}") { entry ->
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            val storeId = requireNotNull(entry.arguments?.getString("storeId"))
            val cart = viewModel { CartViewModel(app.carts, app.checkout, account, storeId) }
            val state by cart.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.checkout.orderId) {
                state.checkout.orderId?.let { orderId ->
                    nav.navigate("order/$orderId") { popUpTo(entry.destination.id) { inclusive = true } }
                }
            }
            CartScreen(
                state,
                change = cart::change,
                review = cart::review,
                dismissReview = cart::dismissReview,
                confirm = cart::confirm,
                recover = cart::recover,
                back = { nav.popBackStack() },
            )
        }
        composable("orders") {
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            val orders = viewModel { OrdersViewModel(app.orders, account) }
            OrdersScreen(
                orders.state.collectAsStateWithLifecycle().value,
                refresh = orders::refresh,
                more = orders::more,
                open = { orderId -> nav.navigate("order/$orderId") },
                back = { nav.popBackStack() },
            )
        }
        composable("order/{orderId}") { entry ->
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            val orderId = requireNotNull(entry.arguments?.getString("orderId"))
            val detail = viewModel { OrderDetailViewModel(app.orders, account, orderId) }
            LaunchedEffect(entry, detail) {
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    detail.resume()
                    try {
                        awaitCancellation()
                    } finally {
                        detail.pause()
                    }
                }
            }
            OrderDetailScreen(
                detail.state.collectAsStateWithLifecycle().value,
                refresh = detail::refresh,
                cancel = detail::cancel,
                back = { nav.popBackStack() },
            )
        }
        composable("pending") {
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            val pending = viewModel { PendingViewModel(app.carts, account.userId) }
            PendingScreen(
                pending.state.collectAsStateWithLifecycle().value,
                open = { storeId -> nav.navigate("cart/$storeId") },
                back = { nav.popBackStack() },
            )
        }
    }
}
