package com.lorongtea.mobile.ui.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lorongtea.mobile.LorongTeaApplication
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.SessionState
import com.lorongtea.mobile.ui.common.RemoteViewModel
import com.lorongtea.mobile.ui.staff.StaffHomeScreen

internal fun NavGraphBuilder.staffNavigation(app: LorongTeaApplication, sessionState: State<SessionState>) {
    val session by sessionState
    navigation(startDestination = "staff-home", route = STAFF) {
        composable("staff-home") {
            val account = session.account?.takeIf { it.role == Role.STAFF } ?: return@composable
            val store = viewModel { RemoteViewModel { app.accounts.assignedStore(account) } }
            val state by store.state.collectAsStateWithLifecycle()
            StaffHomeScreen(state, store::refresh, app.sessions::signOut)
        }
    }
}
