package com.lorongtea.mobile.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.PickupRedemptionResult
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.ScreenHeading

@Composable
internal fun StaffPickupScreen(
    store: Store,
    state: StaffPickupState,
    edit: (String) -> Unit,
    redeem: () -> Unit,
    reset: () -> Unit,
    openOrder: (String) -> Unit,
    back: () -> Unit,
) {
    Page(stringResource(R.string.staff_pickup), back = back) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeading(storeName(store), stringResource(R.string.staff_pickup_description))
            val problem = state.problem?.let { stringResource(it) } ?: state.error
            LoadNotice(state.submitting, problem)
            val result = state.result
            if (result == null) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = edit,
                    label = { Text(stringResource(R.string.pickup_code)) },
                    enabled = !state.submitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                val label = when {
                    state.submitting -> R.string.staff_redeeming
                    state.problem == R.string.staff_pickup_unknown -> R.string.staff_pickup_retry
                    else -> R.string.staff_pickup
                }
                PrimaryAction(stringResource(label), redeem, enabled = state.canSubmit)
            } else {
                val label = when (result.result) {
                    PickupRedemptionResult.REDEEMED -> R.string.staff_pickup_redeemed
                    PickupRedemptionResult.ALREADY_REDEEMED -> R.string.staff_pickup_already
                }
                EmptyNotice(stringResource(label))
                Text(stringResource(R.string.order_number, result.orderId), style = MaterialTheme.typography.titleLarge)
                PrimaryAction(stringResource(R.string.campaign_view_order), { openOrder(result.orderId) })
                OutlinedButton(onClick = reset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.staff_pickup_next))
                }
            }
        }
    }
}
