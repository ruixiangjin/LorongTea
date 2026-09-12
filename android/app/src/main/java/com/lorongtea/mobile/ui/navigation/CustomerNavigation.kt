package com.lorongtea.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.SessionState
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.checkout.CartScreen
import com.lorongtea.mobile.ui.checkout.CartViewModel
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.orders.OrderDetailScreen
import com.lorongtea.mobile.ui.orders.OrderDetailViewModel
import kotlinx.coroutines.awaitCancellation

internal fun NavGraphBuilder.customerNavigation(
    app: LorongTeaApplication,
    nav: NavHostController,
    sessionState: State<SessionState>,
) {
    val session by sessionState
    navigation(startDestination = "home", route = CUSTOMER) {
        composable("home") { entry ->
            val account = session.account?.takeIf { it.role == Role.CUSTOMER } ?: return@composable
            CustomerHome(
                app = app,
                account = account,
                directory = storeDirectory(app, nav, entry),
                openCart = { nav.navigate("cart/$it") { launchSingleTop = true } },
                openOrder = { nav.navigate("order/$it") { launchSingleTop = true } },
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
            val stores by storeDirectory(app, nav, entry).state.collectAsStateWithLifecycle()
            CartScreen(
                state = state,
                store = storeName(stores.data.orEmpty(), storeId),
                change = cart::change,
                review = cart::review,
                dismissReview = cart::dismissReview,
                confirm = cart::confirm,
                recover = cart::recover,
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
            val stores by storeDirectory(app, nav, entry).state.collectAsStateWithLifecycle()
            OrderDetailScreen(
                state = detail.state.collectAsStateWithLifecycle().value,
                stores = stores.data.orEmpty(),
                refresh = detail::refresh,
                cancel = detail::cancel,
                back = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun storeDirectory(
    app: LorongTeaApplication,
    nav: NavHostController,
    entry: NavBackStackEntry,
): RemoteViewModel<List<Store>> {
    val customer = remember(entry) { nav.getBackStackEntry(CUSTOMER) }
    return viewModel(viewModelStoreOwner = customer) { RemoteViewModel(app.catalog::stores) }
}
