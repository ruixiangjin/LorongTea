package com.lorongtea.mobile.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lorongtea.mobile.R

internal enum class CustomerTab(val title: Int, val icon: Int) {
    SHOP(R.string.tab_shop, R.drawable.ic_cup),
    ORDERS(R.string.tab_orders, R.drawable.ic_receipt),
}

@Composable
internal fun CustomerTabs(selected: CustomerTab, select: (CustomerTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        CustomerTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { select(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.title)) },
            )
        }
    }
}
