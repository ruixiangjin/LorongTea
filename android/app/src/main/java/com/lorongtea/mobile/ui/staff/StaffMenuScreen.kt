package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.StaffMenuItem
import com.lorongtea.mobile.data.StaffMenuPatch
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.drinkName
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.RefreshAction
import com.lorongtea.mobile.ui.components.ScreenHeading
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone

@Composable
internal fun StaffMenuScreen(
    store: Store,
    state: StaffMenuState,
    refresh: () -> Unit,
    update: (String, StaffMenuPatch) -> Unit,
    adjust: (String, Int, String) -> Unit,
    recover: () -> Unit,
    back: () -> Unit,
) {
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var stockId by rememberSaveable { mutableStateOf<String?>(null) }
    val menu = state.items.orEmpty()
    val pending = state.pending
    val error = state.problem?.let { stringResource(it) } ?: state.error
    Page(
        stringResource(R.string.staff_menu),
        back = back,
        actions = { RefreshAction(refresh, enabled = !state.busy) },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeading(storeName(store)) }
            if (state.busy || error != null) item { LoadNotice(state.busy, error, refresh) }
            state.notice?.let { item { EmptyNotice(stringResource(it)) } }
            if (pending != null) item(key = "pending-stock") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.staff_stock_pending), style = MaterialTheme.typography.titleMedium)
                        val item = menu.firstOrNull { it.productId == pending.productId }
                        Text(item?.let { drinkName(it.name) } ?: pending.productId)
                        val delta = if (pending.delta > 0) "+${pending.delta}" else pending.delta.toString()
                        Text(stringResource(R.string.staff_stock_pending_detail, delta, pending.reason))
                        PrimaryAction(
                            stringResource(if (state.saving) R.string.staff_saving else R.string.staff_stock_retry),
                            recover,
                            enabled = !state.busy,
                        )
                    }
                }
            }
            if (state.items?.isEmpty() == true) item { EmptyNotice(stringResource(R.string.staff_menu_empty)) }
            items(menu, key = { it.productId }) { item ->
                MenuManagementCard(
                    item = item,
                    canEdit = state.canEdit,
                    canAdjust = state.canEdit && pending == null,
                    edit = { editId = item.productId },
                    adjust = { stockId = item.productId },
                )
            }
        }
    }

    menu.firstOrNull { it.productId == editId }?.let { item ->
        MenuEditDialog(item, state.canEdit, onDismiss = { editId = null }) { patch ->
            editId = null
            update(item.productId, patch)
        }
    }
    menu.firstOrNull { it.productId == stockId }?.let { item ->
        StockEditDialog(item, state.canEdit && pending == null, onDismiss = { stockId = null }) { delta, reason ->
            stockId = null
            adjust(item.productId, delta, reason)
        }
    }
}

@Composable
private fun MenuManagementCard(
    item: StaffMenuItem,
    canEdit: Boolean,
    canAdjust: Boolean,
    edit: () -> Unit,
    adjust: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(drinkName(item.name), style = MaterialTheme.typography.titleLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(money(item.priceCents), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.staff_available_stock, item.availableQty))
                StatusLabel(
                    stringResource(if (item.listed) R.string.staff_listed else R.string.staff_unlisted),
                    if (item.listed) StatusTone.ACTIVE else StatusTone.NEUTRAL,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = edit, enabled = canEdit) { Text(stringResource(R.string.staff_edit_menu)) }
                OutlinedButton(onClick = adjust, enabled = canAdjust) { Text(stringResource(R.string.staff_adjust_stock)) }
            }
        }
    }
}

@Composable
private fun MenuEditDialog(
    item: StaffMenuItem,
    canSave: Boolean,
    onDismiss: () -> Unit,
    save: (StaffMenuPatch) -> Unit,
) {
    val originalPrice = rememberSaveable(item.productId) { item.priceCents }
    val originalListed = rememberSaveable(item.productId) { item.listed }
    var priceText by rememberSaveable(item.productId) { mutableStateOf(money(originalPrice).removePrefix("RM ")) }
    var listed by rememberSaveable(item.productId) { mutableStateOf(originalListed) }
    val price = parsePriceCents(priceText)
    val patch = StaffMenuPatch(price?.takeIf { it != originalPrice }, listed.takeIf { it != originalListed })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.staff_edit_menu)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(drinkName(item.name))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.staff_price_field)) },
                    supportingText = if (price == null) {
                        { Text(stringResource(R.string.staff_price_format)) }
                    } else null,
                    isError = price == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val listing = stringResource(if (listed) R.string.staff_listed else R.string.staff_unlisted)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(listing, Modifier.weight(1f))
                    Switch(
                        checked = listed,
                        onCheckedChange = { listed = it },
                        modifier = Modifier.semantics { contentDescription = listing },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save(patch) }, enabled = canSave && price != null && patch.hasChanges) {
                Text(stringResource(R.string.staff_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.staff_close)) } },
    )
}

@Composable
private fun StockEditDialog(
    item: StaffMenuItem,
    canSave: Boolean,
    onDismiss: () -> Unit,
    save: (Int, String) -> Unit,
) {
    var quantityText by rememberSaveable(item.productId) { mutableStateOf("") }
    var reasonText by rememberSaveable(item.productId) { mutableStateOf("") }
    val delta = quantityText.trim().toIntOrNull()
    val reason = reasonText.trim()
    val valid = delta != null && delta != 0 && reason.isNotEmpty() && reason.length <= 200
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.staff_adjust_stock)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(drinkName(item.name))
                Text(stringResource(R.string.staff_available_stock, item.availableQty))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.staff_stock_delta)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text(stringResource(R.string.staff_stock_reason)) },
                    maxLines = 3,
                    supportingText = { Text(stringResource(R.string.staff_stock_invalid)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { delta?.let { save(it, reason) } }, enabled = canSave && valid) {
                Text(stringResource(R.string.staff_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.staff_close)) } },
    )
}
