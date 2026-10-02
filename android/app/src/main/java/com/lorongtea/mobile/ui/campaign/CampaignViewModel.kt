package com.lorongtea.mobile.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.CampaignRepository
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.common.display
import com.lorongtea.mobile.ui.common.loadRemote
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CampaignPageState(
    val offer: RemoteState<CampaignOffer?> = RemoteState(),
    val receipt: CampaignReceipt = CampaignReceipt.Idle,
    val error: String? = null,
)

internal class CampaignViewModel(
    private val campaigns: CampaignRepository,
    private val session: Session,
    private val storeId: String,
    private val campaignId: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(CampaignPageState())
    private var reading: Job? = null
    private var submitting: Job? = null

    val state = combine(mutable, campaigns.pending(session.userId)) { page, pending ->
        val claim = pending.firstOrNull { it.storeId == storeId && it.campaignId == campaignId }
        if (page.receipt == CampaignReceipt.Idle && claim != null) {
            page.copy(receipt = CampaignReceipt.Unconfirmed(claim.claimId))
        } else {
            page
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CampaignPageState())

    init {
        refresh()
    }

    fun refresh() {
        if (reading?.isActive == true) return
        reading = viewModelScope.launch {
            mutable.update { it.copy(offer = it.offer.copy(loading = true, error = null)) }
            val result = loadRemote(mutable.value.offer) {
                campaigns.offers(session, storeId).firstOrNull { it.campaignId == campaignId }?.toOffer()
            }
            mutable.update { it.copy(offer = result) }
        }
    }

    fun submit(recover: Boolean) {
        if (submitting?.isActive == true) return
        submitting = viewModelScope.launch {
            mutable.update { it.copy(receipt = CampaignReceipt.Submitting, error = null) }
            try {
                val orderId = if (recover) campaigns.recover(session, storeId, campaignId)
                    else campaigns.claim(session, storeId, campaignId)
                val receipt = orderId?.let { CampaignReceipt.OrderCreated(it) } ?: CampaignReceipt.Idle
                mutable.update { it.copy(receipt = receipt) }
            } catch (error: ApiFailure) {
                mutable.update { it.copy(receipt = CampaignReceipt.Idle, error = error.display()) }
                refresh()
            } catch (_: IOException) {
                mutable.update { it.copy(receipt = CampaignReceipt.Idle) }
            }
        }
    }
}
