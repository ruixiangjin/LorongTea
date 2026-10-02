package com.lorongtea.mobile.ui.campaign

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lorongtea.mobile.R
import com.lorongtea.mobile.ui.catalog.DrinkArtwork
import com.lorongtea.mobile.ui.catalog.HOUSE_DRINK
import com.lorongtea.mobile.ui.catalog.drinkName
import com.lorongtea.mobile.ui.common.money
import com.lorongtea.mobile.ui.components.BottomPanel
import com.lorongtea.mobile.ui.components.EmptyNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.StatusLabel
import com.lorongtea.mobile.ui.components.StatusTone
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

@Composable
internal fun CampaignCard(offer: CampaignOffer, open: () -> Unit) {
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DrinkArtwork(offer.drinkName, Modifier.size(width = 72.dp, height = 90.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.campaign_title), style = MaterialTheme.typography.labelLarge)
                Text(drinkName(offer.drinkName), style = MaterialTheme.typography.titleLarge)
                Text(money(offer.priceCents), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(offer.availability.label()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun CampaignDetailScreen(
    offer: CampaignOffer?,
    store: String,
    receipt: CampaignReceipt,
    dispatch: (CampaignAction) -> Unit,
    back: () -> Unit,
    clock: () -> Long = System::currentTimeMillis,
    controls: @Composable () -> Unit = {},
) {
    val time = campaignTime(clock)
    Page(
        title = stringResource(R.string.campaign_title),
        back = back,
        bottomBar = {
            val action = campaignAction(offer, receipt, time.value)
            val label = when (action) {
                CampaignAction.None -> null
                CampaignAction.Claim -> R.string.campaign_claim
                CampaignAction.Refresh -> R.string.campaign_refresh
                is CampaignAction.CheckReceipt -> R.string.campaign_check_receipt
                is CampaignAction.OpenOrder -> R.string.campaign_view_order
            }
            if (label != null || receipt == CampaignReceipt.Submitting) {
                BottomPanel {
                    PrimaryAction(
                        label = stringResource(label ?: R.string.campaign_submitting),
                        onClick = {
                            val latestAction = campaignAction(offer, receipt, clock())
                            if (latestAction == action && action != CampaignAction.None) dispatch(action)
                        },
                        enabled = label != null,
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            controls()
            if (offer != null) CampaignOfferDetails(offer, store, time)
            CampaignReceiptNotice(receipt, time)
        }
    }
}

@Composable
private fun CampaignOfferDetails(offer: CampaignOffer, store: String, time: State<Long>) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        }
    }
    val starts = remember(offer.startsAtMillis, dateFormat) { dateFormat.format(Date(offer.startsAtMillis)) }
    val ends = remember(offer.endsAtMillis, dateFormat) { dateFormat.format(Date(offer.endsAtMillis)) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column {
            if (offer.drinkName == HOUSE_DRINK) {
                Image(
                    painter = painterResource(R.drawable.tea_oolong_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.7f),
                )
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(store, style = MaterialTheme.typography.labelLarge)
                Text(drinkName(offer.drinkName), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.campaign_offer_price, money(offer.priceCents)))
                Text(pluralStringResource(R.plurals.campaign_limit, offer.limitPerCustomer, offer.limitPerCustomer))
                Text(stringResource(R.string.campaign_starts, starts))
                Text(stringResource(R.string.campaign_ends, ends))
                StatusLabel(stringResource(offer.availability.label()), StatusTone.ACTIVE)
                CampaignCountdown(offer, time)
            }
        }
    }
}

@Composable
private fun CampaignCountdown(offer: CampaignOffer, time: State<Long>) {
    val (label, deadline) = when (offer.availability) {
        CampaignAvailability.UPCOMING -> R.string.campaign_opens_in to offer.startsAtMillis
        CampaignAvailability.OPEN -> R.string.campaign_ends_in to offer.endsAtMillis
        else -> return
    }
    Text(
        stringResource(label, formatCountdown(remainingSeconds(deadline, time.value))),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun CampaignReceiptNotice(receipt: CampaignReceipt, time: State<Long>) {
    val label = when (receipt) {
        CampaignReceipt.Idle, CampaignReceipt.Submitting -> return
        is CampaignReceipt.Accepted -> R.string.campaign_accepted
        is CampaignReceipt.Unconfirmed -> R.string.campaign_unknown
        is CampaignReceipt.OrderCreated -> R.string.campaign_confirmed
        CampaignReceipt.ServiceUnavailable -> R.string.campaign_unavailable
        CampaignReceipt.Released -> R.string.campaign_released
        is CampaignReceipt.RateLimited -> {
            val remaining = remainingSeconds(receipt.retryAtMillis, time.value)
            val notice = if (remaining == 0L) stringResource(R.string.campaign_retry_ready)
                else stringResource(R.string.campaign_retry_after, formatCountdown(remaining))
            EmptyNotice(notice)
            return
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            if (receipt is CampaignReceipt.Unconfirmed || receipt is CampaignReceipt.Accepted) {
                Text(stringResource(R.string.campaign_receipt_description))
            }
        }
    }
}

@Composable
private fun campaignTime(clock: () -> Long): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestClock by rememberUpdatedState(clock)
    return produceState(clock(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = latestClock()
                delay(500)
            }
        }
    }
}

private fun formatCountdown(seconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)

private fun CampaignAvailability.label(): Int = when (this) {
    CampaignAvailability.UPCOMING -> R.string.campaign_upcoming
    CampaignAvailability.OPEN -> R.string.campaign_open
    CampaignAvailability.SOLD_OUT -> R.string.campaign_sold_out
    CampaignAvailability.ENDED -> R.string.campaign_ended
    CampaignAvailability.LIMIT_REACHED -> R.string.campaign_limit_reached
}
