package com.lorongtea.mobile.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Store

@Composable
internal fun storeName(store: Store): String = when (store.code) {
    "bukit-bintang" -> stringResource(R.string.store_bukit_bintang)
    "petaling-jaya" -> stringResource(R.string.store_petaling_jaya)
    else -> store.name
}

@Composable
internal fun storeName(stores: List<Store>, storeId: String): String =
    stores.firstOrNull { it.id == storeId }?.let { storeName(it) }
        ?: stringResource(R.string.store_number, storeId)
