package com.lorongtea.mobile.ui.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.PendingCheckout
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PendingViewModel(carts: CartRepository, accountId: String) : ViewModel() {
    val state = carts.observePending(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PendingScreen(
    pending: List<PendingCheckout>?,
    stores: List<Store>,
    open: (String) -> Unit,
    back: () -> Unit,
) {
    Page(stringResource(R.string.pending_orders), back = back) {
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                pending == null -> item { LoadNotice(true, null) }
                pending.isEmpty() -> item { EmptyNotice(stringResource(R.string.pending_empty)) }
                else -> item {
                    Text(
                        stringResource(R.string.pending_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(pending.orEmpty(), key = { it.key }) { order ->
                Surface(
                    onClick = { open(order.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(storeName(stores, order.storeId), style = MaterialTheme.typography.titleLarge)
                            val status = if (order.priceChanged) R.string.order_prices_changed
                                else R.string.order_waiting_confirmation
                            StatusLabel(stringResource(status), StatusTone.ATTENTION)
                        }
                        Icon(painterResource(R.drawable.ic_arrow), contentDescription = null)
                    }
                }
            }
        }
    }
}
