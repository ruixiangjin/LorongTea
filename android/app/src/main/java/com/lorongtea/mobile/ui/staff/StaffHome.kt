package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page

@Composable
internal fun StaffHomeScreen(state: RemoteState<Store>, refresh: () -> Unit, signOut: () -> Unit) {
    Page("Your store", actions = {
        TextButton(onClick = refresh, enabled = !state.loading) { Text("Refresh") }
        TextButton(onClick = signOut) { Text("Sign out") }
    }) {
        Column {
            LoadNotice(state.loading, state.error)
            state.data?.let { Text(it.name) }
        }
    }
}
