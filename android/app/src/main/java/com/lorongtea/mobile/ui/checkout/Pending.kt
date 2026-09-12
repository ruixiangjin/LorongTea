package com.lorongtea.mobile.ui.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PendingViewModel(carts: CartRepository, accountId: String) : ViewModel() {
    val state = carts.observePending(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
internal fun PendingCheckoutCard(order: PendingCheckout, store: String, open: () -> Unit) {
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(store, style = MaterialTheme.typography.titleLarge)
                val status = if (order.priceChanged) R.string.order_prices_changed
                    else R.string.order_waiting_confirmation
                Text(stringResource(status), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (order.priceChanged) R.string.order_refresh_prices else R.string.checkout_find_order),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Icon(painterResource(R.drawable.ic_arrow), contentDescription = null)
        }
    }
}
