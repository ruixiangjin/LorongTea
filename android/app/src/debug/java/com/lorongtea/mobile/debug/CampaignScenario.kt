package com.lorongtea.mobile.debug

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.campaign.CampaignAction
import com.lorongtea.mobile.ui.campaign.CampaignAvailability
import com.lorongtea.mobile.ui.campaign.CampaignDetailScreen
import com.lorongtea.mobile.ui.campaign.CampaignOffer
import com.lorongtea.mobile.ui.campaign.CampaignReceipt
import com.lorongtea.mobile.ui.catalog.HOUSE_DRINK
import com.lorongtea.mobile.ui.catalog.storeName
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.delay

internal enum class CampaignExample(val label: Int) {
    STORE_STATUS(R.string.lab_campaign_store_status),
    UPCOMING(R.string.campaign_upcoming),
    OPEN(R.string.campaign_open),
    SOLD_OUT(R.string.campaign_sold_out),
    ENDED(R.string.campaign_ended),
    LIMIT_REACHED(R.string.campaign_limit_reached),
    UNCONFIRMED(R.string.campaign_unknown),
    ACCEPTED(R.string.campaign_accepted),
    RATE_LIMIT(R.string.lab_campaign_rate_limit),
    UNAVAILABLE(R.string.lab_campaign_unavailable),
    RELEASED(R.string.lab_campaign_released),
    CREATED(R.string.campaign_confirmed),
}

private enum class DemoReceipt {
    IDLE, SUBMITTING, UNCONFIRMED, ACCEPTED, RATE_LIMITED, UNAVAILABLE, RELEASED, CREATED;

    fun snapshot(claimId: String, orderId: String, retryAt: Long): CampaignReceipt = when (this) {
        IDLE -> CampaignReceipt.Idle
        SUBMITTING -> CampaignReceipt.Submitting
        UNCONFIRMED -> CampaignReceipt.Unconfirmed(claimId)
        ACCEPTED -> CampaignReceipt.Accepted(claimId)
        RATE_LIMITED -> CampaignReceipt.RateLimited(retryAt)
        UNAVAILABLE -> CampaignReceipt.ServiceUnavailable
        RELEASED -> CampaignReceipt.Released
        CREATED -> CampaignReceipt.OrderCreated(orderId)
    }
}

@Composable
internal fun CampaignScenario(back: () -> Unit, initialStore: Int = 0) {
    var storeIndex by rememberSaveable { mutableIntStateOf(initialStore) }
    var example by rememberSaveable { mutableStateOf(CampaignExample.STORE_STATUS) }
    var version by rememberSaveable { mutableIntStateOf(0) }
    val store = sampleStores[storeIndex]
    key(store.id, example, version) {
        val opening = rememberSaveable { fridayOpening() }
        val base = remember { sampleOffer(store, opening, example) }
        var availability by rememberSaveable { mutableStateOf(base.availability) }
        val offer = base.copy(availability = availability)
        val startClock = remember {
            when (example) {
                CampaignExample.UPCOMING -> opening - 15_000
                CampaignExample.ENDED -> base.endsAtMillis
                else -> opening + 15_000
            }
        }
        val elapsedStart = rememberSaveable { SystemClock.elapsedRealtime() }
        var advance by rememberSaveable { mutableLongStateOf(0L) }
        val clock = { startClock + SystemClock.elapsedRealtime() - elapsedStart + advance }
        val claimId = "demo-claim-${store.id}"
        val orderId = "DEMO-CAMPAIGN-${store.id}"
        var demoReceipt by rememberSaveable {
            mutableStateOf(
                when (example) {
                    CampaignExample.UNCONFIRMED -> DemoReceipt.UNCONFIRMED
                    CampaignExample.ACCEPTED -> DemoReceipt.ACCEPTED
                    CampaignExample.RATE_LIMIT -> DemoReceipt.RATE_LIMITED
                    CampaignExample.UNAVAILABLE -> DemoReceipt.UNAVAILABLE
                    CampaignExample.RELEASED -> DemoReceipt.RELEASED
                    CampaignExample.CREATED -> DemoReceipt.CREATED
                    else -> DemoReceipt.IDLE
                },
            )
        }
        val receipt = demoReceipt.snapshot(claimId, orderId, startClock + 15_000)
        var openedOrder by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(demoReceipt) {
            if (demoReceipt == DemoReceipt.SUBMITTING) {
                delay(450)
                demoReceipt = DemoReceipt.CREATED
            }
        }
        openedOrder?.let { id ->
            BackHandler { openedOrder = null }
            OrderScenario({ openedOrder = null }, store, id, offer.priceCents)
            return
        }
        ScenarioFrame(
            controls = {
                ScenarioPicker(sampleStores, store, { storeName(it) }) { storeIndex = sampleStores.indexOf(it) }
                ScenarioPicker(CampaignExample.entries, example, { stringResource(it.label) }) { example = it }
                TextButton(onClick = { advance += 20_000 }) { Text(stringResource(R.string.lab_time_advance)) }
                TextButton(onClick = { version++ }) { Text(stringResource(R.string.lab_reset)) }
            },
        ) {
            CampaignDetailScreen(
                offer = offer,
                store = storeName(store),
                receipt = receipt,
                back = back,
                clock = clock,
                dispatch = { action ->
                    when (action) {
                        CampaignAction.Claim -> demoReceipt = DemoReceipt.SUBMITTING
                        CampaignAction.Refresh -> {
                            val now = clock()
                            availability = when {
                                now >= offer.endsAtMillis -> CampaignAvailability.ENDED
                                now >= offer.startsAtMillis -> CampaignAvailability.OPEN
                                else -> CampaignAvailability.UPCOMING
                            }
                        }
                        is CampaignAction.CheckReceipt -> if (action.claimId == claimId) {
                            demoReceipt = DemoReceipt.CREATED
                        }
                        is CampaignAction.OpenOrder -> openedOrder = action.orderId
                        CampaignAction.None -> Unit
                    }
                },
            )
        }
    }
}

internal fun sampleOffer(store: Store, opening: Long, example: CampaignExample): CampaignOffer {
    val availability = when (example) {
        CampaignExample.STORE_STATUS -> if (store.id == "1") CampaignAvailability.OPEN else CampaignAvailability.SOLD_OUT
        CampaignExample.UPCOMING -> CampaignAvailability.UPCOMING
        CampaignExample.SOLD_OUT -> CampaignAvailability.SOLD_OUT
        CampaignExample.ENDED -> CampaignAvailability.ENDED
        CampaignExample.LIMIT_REACHED -> CampaignAvailability.LIMIT_REACHED
        else -> CampaignAvailability.OPEN
    }
    return CampaignOffer("friday-oolong", store.id, HOUSE_DRINK, 690, opening, opening + 1_200_000, availability)
}

internal fun fridayOpening(): Long = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply {
    while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 15)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
