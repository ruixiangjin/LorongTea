package com.lorongtea.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.SessionState
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.staff.StaffHomeScreen
import com.lorongtea.mobile.ui.staff.StaffMenuScreen
import com.lorongtea.mobile.ui.staff.StaffMenuViewModel
import com.lorongtea.mobile.ui.staff.StaffOrderScreen
import com.lorongtea.mobile.ui.staff.StaffOrderViewModel
import com.lorongtea.mobile.ui.staff.StaffQueueViewModel
import com.lorongtea.mobile.ui.staff.StaffPickupScreen
import com.lorongtea.mobile.ui.staff.StaffPickupViewModel
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
                val orders = viewModel(key = store.id) {
                    StaffQueueViewModel { status, limit, offset ->
                        app.staffOrders.queue(account, store.id, status, limit, offset)
                    }
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { orders.refresh() }
                StaffHomeScreen(
                    store = store,
                    queue = orders.state.collectAsStateWithLifecycle().value,
                    refresh = orders::refresh,
                    filter = orders::select,
                    more = orders::more,
                    open = { orderId -> nav.navigate("staff-order/$orderId") { launchSingleTop = true } },
                    signOut = app.sessions::signOut,
                    shortcuts = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { nav.navigate("staff-redeem") { launchSingleTop = true } }) {
                                Text(stringResource(R.string.staff_pickup))
                            }
                            OutlinedButton(onClick = { nav.navigate("staff-menu") { launchSingleTop = true } }) {
                                Text(stringResource(R.string.staff_menu))
                            }
                        }
                    },
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
            LaunchedEffect(entry, order) {
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { order.observeWhileVisible() }
            }
            StaffOrderScreen(
                store, orderId, order.state.collectAsStateWithLifecycle().value,
                refresh = order::refresh,
                advance = order::advance,
                confirmPayment = order::confirmPayment,
                openPickup = { nav.navigate("staff-redeem") { launchSingleTop = true } },
                back = { nav.popBackStack() },
            )
        }
        composable("staff-redeem") { entry ->
            val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
            val assignment = staffAssignment(app, account, nav, entry)
            val state by assignment.state.collectAsStateWithLifecycle()
            val store = state.data
            if (store == null) {
                StaffStoreLoading(state, assignment::refresh, app.sessions::signOut)
                return@composable
            }
            val pickup = viewModel {
                StaffPickupViewModel { code -> app.staffOrders.redeem(account, store.id, code) }
            }
            StaffPickupScreen(
                store = store,
                state = pickup.state.collectAsStateWithLifecycle().value,
                edit = pickup::edit,
                redeem = pickup::redeem,
                reset = pickup::reset,
                back = { nav.popBackStack() },
                openOrder = { orderId ->
                    val previous = nav.previousBackStackEntry
                    if (previous?.destination?.route == "staff-order/{orderId}" &&
                        previous.arguments?.getString("orderId") == orderId
                    ) {
                        nav.popBackStack()
                    } else {
                        nav.navigate("staff-order/$orderId") { popUpTo("staff-redeem") { inclusive = true } }
                    }
                },
            )
        }
        composable("staff-menu") { entry ->
            val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
            val assignment = staffAssignment(app, account, nav, entry)
            val state by assignment.state.collectAsStateWithLifecycle()
            val store = state.data
            if (store == null) {
                StaffStoreLoading(state, assignment::refresh, app.sessions::signOut)
                return@composable
            }
            val menu = viewModel { StaffMenuViewModel(app.staffInventory.forStore(account, store.id)) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { menu.refresh() }
            StaffMenuScreen(
                store = store,
                state = menu.state.collectAsStateWithLifecycle().value,
                refresh = menu::refresh,
                update = menu::update,
                adjust = menu::adjust,
                recover = menu::recover,
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
