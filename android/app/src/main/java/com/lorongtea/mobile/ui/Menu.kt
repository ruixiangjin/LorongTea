package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.lorongtea.mobile.data.MenuItem
import java.util.Locale

@Composable
fun MenuScreen(state: RemoteState<List<MenuItem>>, refresh: () -> Unit, back: () -> Unit) {
    Page("Menu", back = back, actions = {
        TextButton(onClick = refresh, enabled = !state.loading) { Text("Refresh") }
    }) {
        Column {
            LoadNotice(state.loading, state.error)
            if (state.data?.isEmpty() == true) Text("This store has an empty menu.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.data.orEmpty(), key = { it.productId }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (item.listed) money(item.priceCents) else "Unavailable")
                        }
                    }
                }
            }
        }
    }
}

internal fun money(cents: Long): String = String.format(Locale.ROOT, "RM %d.%02d", cents / 100, cents % 100)
