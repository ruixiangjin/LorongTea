package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.data.Store

@Composable
fun StoresScreen(
    state: RemoteState<List<Store>>,
    refresh: () -> Unit,
    select: ((Store) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    shortcuts: @Composable RowScope.() -> Unit = {},
) {
    Page("Choose a store", actions = {
        TextButton(onClick = refresh, enabled = !state.loading) { Text("Refresh") }
        actions()
    }) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = shortcuts)
            LoadNotice(state.loading, state.error)
            if (state.data?.isEmpty() == true) Text("No stores are available.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.data.orEmpty(), key = { it.id }) { store ->
                    val name = when (store.code) {
                        "bukit-bintang" -> "Bukit Bintang"
                        "petaling-jaya" -> "Petaling Jaya"
                        else -> store.name
                    }
                    Card(
                        onClick = { select?.invoke(store) },
                        enabled = select != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            if (name != store.name) Text(store.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
