package com.lorongtea.mobile.data

import java.util.Date
import java.util.UUID

internal data class CampaignDto(
    val campaignId: String,
    val storeId: String,
    val drinkName: String,
    val priceCents: Long,
    val startsAt: Date,
    val endsAt: Date,
    val availability: String,
    val limitPerCustomer: Int,
)

internal data class ClaimRequest(val claimId: String)

internal data class ClaimReceipt(
    val claimId: String,
    val storeId: String,
    val campaignId: String,
    val status: String,
    val orderId: String?,
)

internal class CampaignRepository(private val backend: Backend, private val pending: PendingCampaignDao) {
    suspend fun offers(session: Session, storeId: String): List<CampaignDto> =
        backend.request(read = true, session = session) { campaigns(session.authorization, storeId).data }

    fun pending(accountId: String) = pending.observe(accountId)

    suspend fun claim(session: Session, storeId: String, campaignId: String): String? {
        val candidate = PendingCampaignClaim(
            session.userId, storeId, campaignId, UUID.randomUUID().toString(), System.currentTimeMillis(),
        )
        if (pending.insert(candidate) == -1L) return recover(session, storeId, campaignId)
        return submit(session, candidate)
    }

    suspend fun recover(session: Session, storeId: String, campaignId: String): String? {
        val claim = pending.find(session.userId, storeId, campaignId) ?: return null
        val receipt = try {
            backend.request(read = true, session = session) {
                claimReceipt(session.authorization, claim.claimId).data
            }
        } catch (error: ApiFailure) {
            if (error.status == 404 && error.code == "CLAIM_NOT_FOUND") return submit(session, claim)
            throw error
        }
        return complete(claim, receipt)
    }

    private suspend fun submit(session: Session, claim: PendingCampaignClaim): String {
        val receipt = try {
            backend.request(read = false, session = session) {
                claimCampaign(session.authorization, claim.storeId, claim.campaignId, ClaimRequest(claim.claimId)).data
            }
        } catch (error: ApiFailure) {
            if (error.code in rejectedClaimCodes) remove(claim)
            throw error
        }
        return complete(claim, receipt)
    }

    private suspend fun complete(claim: PendingCampaignClaim, receipt: ClaimReceipt): String {
        val orderId = receipt.orderId
        if (receipt.status != "ORDER_CREATED" || orderId.isNullOrBlank() || receipt.claimId != claim.claimId ||
            receipt.storeId != claim.storeId || receipt.campaignId != claim.campaignId
        ) {
            throw ApiFailure(502, null, null, "The server returned an unexpected claim receipt.")
        }
        remove(claim)
        return orderId
    }

    private suspend fun remove(claim: PendingCampaignClaim) {
        pending.delete(claim.accountId, claim.storeId, claim.campaignId, claim.claimId)
    }
}

private val rejectedClaimCodes = setOf(
    "INVALID_REQUEST", "STORE_NOT_FOUND", "CAMPAIGN_NOT_FOUND", "CAMPAIGN_NOT_STARTED",
    "CAMPAIGN_ENDED", "CAMPAIGN_LIMIT_REACHED", "CAMPAIGN_SOLD_OUT", "IDEMPOTENCY_CONFLICT",
)
