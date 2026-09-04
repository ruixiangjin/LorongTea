package com.lorongtea.mobile.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.PendingCheckout
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PendingViewModel(carts: CartRepository, accountId: String) : ViewModel() {
    val state = carts.observePending(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PendingScreen(pending: List<PendingCheckout>?, open: (String) -> Unit, back: () -> Unit) {
    Page("Pending orders", back = back) {
        Column {
            LoadNotice(pending == null, null)
            if (pending?.isEmpty() == true) Text("You have no pending orders.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pending.orEmpty(), key = { it.key }) { order ->
                    Card(onClick = { open(order.storeId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Store #${order.storeId}")
                            Text(if (order.priceChanged) "Prices need refreshing" else "Awaiting confirmation")
                        }
                    }
                }
            }
        }
    }
}
