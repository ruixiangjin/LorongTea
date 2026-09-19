package com.lorongtea.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.SessionState
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.staff.StaffHomeScreen
import com.lorongtea.mobile.ui.staff.StaffOrderScreen
import com.lorongtea.mobile.ui.staff.StaffOrderViewModel
import com.lorongtea.mobile.ui.staff.StaffQueueViewModel
import com.lorongtea.mobile.ui.staff.StaffStoreLoading

internal fun NavGraphBuilder.staffNavigation(
    app: LorongTeaApplication,
    nav: NavHostController,
    sessionState: State<SessionState>,
) {
    val session by sessionState
    navigation(startDestination = "staff-home", route = STAFF) {
        composable("staff-home") { entry ->
            val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
            val assignment = staffAssignment(app, account, nav, entry)
            val state by assignment.state.collectAsStateWithLifecycle()
            val store = state.data
            if (store == null) {
                StaffStoreLoading(state, assignment::refresh, app.sessions::signOut)
            } else {
                val orders = viewModel(key = store.id) { StaffQueueViewModel(app.staffOrders, account, store.id) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { orders.refresh() }
                StaffHomeScreen(
                    store = store,
                    queue = orders.state.collectAsStateWithLifecycle().value,
                    refresh = orders::refresh,
                    filter = orders::select,
                    more = orders::more,
                    open = { orderId -> nav.navigate("staff-order/$orderId") { launchSingleTop = true } },
                    signOut = app.sessions::signOut,
                )
            }
        }
        composable("staff-order/{orderId}") { entry ->
            val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
            val assignment = staffAssignment(app, account, nav, entry)
            val state by assignment.state.collectAsStateWithLifecycle()
            val store = state.data
            if (store == null) {
                StaffStoreLoading(state, assignment::refresh, app.sessions::signOut)
                return@composable
            }
            val orderId = requireNotNull(entry.arguments?.getString("orderId"))
            val order = viewModel { StaffOrderViewModel(app.staffOrders.forStore(account, store.id), orderId) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { order.refresh() }
            StaffOrderScreen(
                store, orderId, order.state.collectAsStateWithLifecycle().value,
                refresh = order::refresh,
                advance = order::advance,
                confirmPayment = order::confirmPayment,
                back = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun staffAssignment(
    app: LorongTeaApplication,
    account: Session,
    nav: NavHostController,
    entry: NavBackStackEntry,
): RemoteViewModel<Store> {
    val graph = remember(entry) { nav.getBackStackEntry(STAFF) }
    return viewModel(viewModelStoreOwner = graph, key = "store/${account.userId}") {
        RemoteViewModel { app.accounts.assignedStore(account) }
    }
}
