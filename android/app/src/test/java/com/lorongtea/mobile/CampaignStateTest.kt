package com.lorongtea.mobile

import com.lorongtea.mobile.ui.campaign.CampaignAction
import com.lorongtea.mobile.ui.campaign.CampaignAvailability
import com.lorongtea.mobile.ui.campaign.CampaignOffer
import com.lorongtea.mobile.ui.campaign.CampaignReceipt
import com.lorongtea.mobile.ui.campaign.campaignAction
import com.lorongtea.mobile.ui.campaign.forStore
import com.lorongtea.mobile.ui.campaign.remainingSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignStateTest {
    @Test
    fun countdownCrossingStartAsksForRefreshBeforeEnablingClaim() {
        val upcoming = offer(CampaignAvailability.UPCOMING)
        assertEquals(CampaignAction.None, campaignAction(upcoming, CampaignReceipt.Idle, 9_999))
        assertEquals(CampaignAction.Refresh, campaignAction(upcoming, CampaignReceipt.Idle, 10_000))
        assertEquals(CampaignAction.Claim, campaignAction(offer(CampaignAvailability.OPEN), CampaignReceipt.Idle, 10_000))
    }

    @Test
    fun expiredOpenSnapshotDoesNotOfferAClaim() {
        assertEquals(CampaignAction.Refresh, campaignAction(offer(CampaignAvailability.OPEN), CampaignReceipt.Idle, 20_000))
    }

    @Test
    fun soldOutEndedAndLimitReachedDoNotOfferNewClaims() {
        for (status in listOf(CampaignAvailability.SOLD_OUT, CampaignAvailability.ENDED, CampaignAvailability.LIMIT_REACHED)) {
            assertEquals(CampaignAction.None, campaignAction(offer(status), CampaignReceipt.Idle, 15_000))
        }
    }

    @Test
    fun onlyOffersForTheActiveStoreAreShown() {
        val offer = offer(CampaignAvailability.OPEN)
        assertEquals(offer, offer.forStore("1"))
        assertNull(offer.forStore("2"))
    }

    @Test
    fun unfinishedRequestsKeepTheirOriginalReceiptEvenAfterTheOfferEnds() {
        val offer = offer(CampaignAvailability.ENDED)
        val action = CampaignAction.CheckReceipt("original-id")
        assertEquals(action, campaignAction(offer, CampaignReceipt.Unconfirmed("original-id"), 30_000))
        assertEquals(action, campaignAction(offer, CampaignReceipt.Accepted("original-id"), 30_000))
    }

    @Test
    fun submittingCannotSendAgainAndConfirmedReceiptOpensTheOrder() {
        val offer = offer(CampaignAvailability.OPEN)
        assertEquals(CampaignAction.None, campaignAction(offer, CampaignReceipt.Submitting, 15_000))
        assertEquals(
            CampaignAction.OpenOrder("88"),
            campaignAction(offer, CampaignReceipt.OrderCreated("88"), 15_000),
        )
    }

    @Test
    fun retryAfterKeepsClaimDisabledUntilAllowed() {
        val offer = offer(CampaignAvailability.OPEN)
        val receipt = CampaignReceipt.RateLimited(16_000)
        assertEquals(CampaignAction.None, campaignAction(offer, receipt, 15_999))
        assertEquals(CampaignAction.Claim, campaignAction(offer, receipt, 16_000))
    }

    @Test
    fun releasedAndUnavailableRequestsOfferRetryWhileTheOfferIsOpen() {
        val offer = offer(CampaignAvailability.OPEN)
        assertEquals(CampaignAction.Claim, campaignAction(offer, CampaignReceipt.Released, 15_000))
        assertEquals(CampaignAction.Claim, campaignAction(offer, CampaignReceipt.ServiceUnavailable, 15_000))
    }

    @Test
    fun countdownNeverShowsZeroEarlyOrNegativeTime() {
        assertEquals(2L, remainingSeconds(2_001, 1_000))
        assertEquals(1L, remainingSeconds(1_001, 1_000))
        assertEquals(0L, remainingSeconds(1_000, 1_000))
        assertEquals(0L, remainingSeconds(999, 1_000))
    }

    private fun offer(availability: CampaignAvailability) =
        CampaignOffer("friday", "1", "oolong", 690, 10_000, 20_000, availability)
}
