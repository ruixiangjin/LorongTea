package com.lorongtea.mobile.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.ScreenHeading

@Composable
fun StoresScreen(
    state: RemoteState<List<Store>>,
    refresh: () -> Unit,
    select: (Store) -> Unit,
    signOut: () -> Unit,
    openOrders: () -> Unit,
    openPending: () -> Unit,
) {
    Page(stringResource(R.string.brand_name), actions = {
        RefreshAction(refresh, enabled = !state.loading)
        TextButton(onClick = signOut) { Text(stringResource(R.string.sign_out)) }
    }) {
        LazyColumn(
            contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeading(
                    stringResource(R.string.choose_store),
                    stringResource(R.string.choose_store_description),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = openOrders) { Text(stringResource(R.string.my_orders)) }
                    OutlinedButton(onClick = openPending) { Text(stringResource(R.string.pending_orders)) }
                }
            }
            if (state.loading || state.error != null) {
                item { LoadNotice(state.loading, state.error, refresh) }
            }
            if (state.data?.isEmpty() == true) {
                item { EmptyNotice(stringResource(R.string.store_none)) }
            }
            items(state.data.orEmpty(), key = { it.id }) { store ->
                Surface(
                    onClick = { select(store) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_store), contentDescription = null)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(storeName(store), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.store_pickup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_arrow),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
