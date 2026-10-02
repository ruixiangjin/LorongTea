package com.lorongtea.mobile.ui.campaign

import com.lorongtea.mobile.data.CampaignDto

internal enum class CampaignAvailability { UPCOMING, OPEN, SOLD_OUT, ENDED, LIMIT_REACHED }

internal data class CampaignOffer(
    val campaignId: String,
    val storeId: String,
    val drinkName: String,
    val priceCents: Long,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val availability: CampaignAvailability,
    val limitPerCustomer: Int = 1,
) {
    init {
        require(startsAtMillis < endsAtMillis)
        require(priceCents >= 0 && limitPerCustomer > 0)
    }
}

internal fun CampaignOffer.forStore(activeStoreId: String): CampaignOffer? = takeIf { storeId == activeStoreId }

internal sealed interface CampaignReceipt {
    data object Idle : CampaignReceipt
    data object Submitting : CampaignReceipt
    data class Unconfirmed(val claimId: String) : CampaignReceipt
    data class Accepted(val claimId: String) : CampaignReceipt
    data class OrderCreated(val orderId: String) : CampaignReceipt {
        init { require(orderId.isNotBlank()) }
    }
    data class RateLimited(val retryAtMillis: Long) : CampaignReceipt
    data object ServiceUnavailable : CampaignReceipt
    data object Released : CampaignReceipt
}

internal sealed interface CampaignAction {
    data object None : CampaignAction
    data object Claim : CampaignAction
    data object Refresh : CampaignAction
    data class CheckReceipt(val claimId: String) : CampaignAction
    data class OpenOrder(val orderId: String) : CampaignAction
}

internal fun campaignAction(offer: CampaignOffer?, receipt: CampaignReceipt, now: Long): CampaignAction =
    when (receipt) {
        is CampaignReceipt.OrderCreated -> CampaignAction.OpenOrder(receipt.orderId)
        is CampaignReceipt.Unconfirmed -> CampaignAction.CheckReceipt(receipt.claimId)
        is CampaignReceipt.Accepted -> CampaignAction.CheckReceipt(receipt.claimId)
        CampaignReceipt.Submitting -> CampaignAction.None
        is CampaignReceipt.RateLimited -> {
            if (now < receipt.retryAtMillis) CampaignAction.None else offer?.availableAction(now) ?: CampaignAction.None
        }
        else -> offer?.availableAction(now) ?: CampaignAction.None
    }

private fun CampaignOffer.availableAction(now: Long): CampaignAction = when (availability) {
    CampaignAvailability.UPCOMING -> if (now >= startsAtMillis) CampaignAction.Refresh else CampaignAction.None
    CampaignAvailability.OPEN -> if (now < endsAtMillis) CampaignAction.Claim else CampaignAction.Refresh
    CampaignAvailability.SOLD_OUT, CampaignAvailability.ENDED, CampaignAvailability.LIMIT_REACHED -> CampaignAction.None
}

internal fun remainingSeconds(deadlineMillis: Long, nowMillis: Long): Long {
    if (nowMillis >= deadlineMillis) return 0L
    val remaining = deadlineMillis - nowMillis
    return remaining / 1_000 + if (remaining % 1_000 == 0L) 0 else 1
}

internal fun CampaignDto.toOffer() = CampaignOffer(
    campaignId, storeId, drinkName, priceCents, startsAt.time, endsAt.time,
    CampaignAvailability.valueOf(availability), limitPerCustomer,
)
