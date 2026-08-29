package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role

private const val LOGIN = "login"
private const val CUSTOMER = "customer"
private const val STAFF = "staff"

@Composable
fun AppNavigation(app: LorongTeaApplication) {
    val session by app.sessions.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val target = when (session.account?.role) {
        Role.CUSTOMER -> CUSTOMER
        Role.STAFF -> STAFF
        null -> LOGIN
    }
    LaunchedEffect(target) {
        if (nav.currentDestination?.hierarchy?.any { it.route == target } != true) {
            nav.navigate(target) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(nav, startDestination = LOGIN) {
        composable(LOGIN) {
            val login = viewModel { LoginViewModel(app.accounts) }
            LoginScreen(login.state.collectAsStateWithLifecycle().value, session.expired, login::signIn)
        }
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
                })
                proposedStore?.let { storeId ->
                    AlertDialog(
                        onDismissRequest = { proposedStore = null },
                        title = { Text("Switch stores?") },
                        text = { Text("The menu and prices depend on the store.") },
                        confirmButton = { TextButton(onClick = { openStore(storeId) }) { Text("Switch store") } },
                        dismissButton = { TextButton(onClick = { proposedStore = null }) { Text("Stay here") } },
                    )
                }
            }
            composable("menu/{storeId}") { entry ->
                if (session.account?.role != Role.CUSTOMER) return@composable
                val storeId = requireNotNull(entry.arguments?.getString("storeId"))
                val menu = viewModel { RemoteViewModel { app.catalog.menu(storeId) } }
                MenuScreen(menu.state.collectAsStateWithLifecycle().value, menu::refresh, back = { nav.popBackStack() })
            }
        }
        navigation(startDestination = "staff-home", route = STAFF) {
            composable("staff-home") {
                val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
                val store = viewModel { RemoteViewModel { app.accounts.assignedStore(account) } }
                val state by store.state.collectAsStateWithLifecycle()
                Page("Your store", actions = {
                    TextButton(onClick = store::refresh, enabled = !state.loading) { Text("Refresh") }
                    TextButton(onClick = app.sessions::signOut) { Text("Sign out") }
                }) {
                    Column {
                        LoadNotice(state.loading, state.error)
                        state.data?.let { Text(it.name) }
                    }
                }
            }
        }
    }
}
