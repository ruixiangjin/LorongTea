package com.lorongtea.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.ui.auth.LoginScreen
import com.lorongtea.mobile.ui.auth.LoginViewModel

private const val LOGIN = "login"
internal const val CUSTOMER = "customer"
internal const val STAFF = "staff"

@Composable
fun AppNavigation(app: LorongTeaApplication) {
    val sessionState = app.sessions.state.collectAsStateWithLifecycle()
    val session by sessionState
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
        customerNavigation(app, nav, sessionState)
        staffNavigation(app, sessionState)
    }
}
