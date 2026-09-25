package com.lorongtea.mobile.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.PendingStockAdjustment
import com.lorongtea.mobile.data.StaffInventory
import com.lorongtea.mobile.data.StaffMenuItem
import com.lorongtea.mobile.data.StaffMenuPatch
import com.lorongtea.mobile.data.StockOutcome
import com.lorongtea.mobile.data.isDefinitiveRejection
import com.lorongtea.mobile.ui.common.display
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class StaffMenuState(
    val items: List<StaffMenuItem>? = null,
    val pending: PendingStockAdjustment? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val verifyMenu: Boolean = false,
    val notice: Int? = null,
    val problem: Int? = null,
    val error: String? = null,
) {
    val busy get() = loading || saving
    val canEdit get() = !busy && !verifyMenu
}

internal class StaffMenuViewModel(private val inventory: StaffInventory) : ViewModel() {
    private val mutable = MutableStateFlow(StaffMenuState())
    val state = mutable.asStateFlow()

    fun refresh() = run(saving = false) { readMenu() }

    fun update(productId: String, patch: StaffMenuPatch) {
        if (!mutable.value.canEdit || !patch.hasChanges) return
        run(saving = true) {
            try {
                val updated = inventory.update(productId, patch)
                mutable.value = mutable.value.copy(
                    items = mutable.value.items.orEmpty().map { if (it.productId == productId) updated else it },
                    notice = R.string.staff_menu_saved,
                )
            } catch (failure: ApiFailure) {
                if (failure.isDefinitiveRejection() || failure.status == 401) throw failure
                verify(productId, patch)
            } catch (_: IOException) {
                verify(productId, patch)
            }
        }
    }

    fun adjust(productId: String, delta: Int, reason: String) {
        if (!mutable.value.canEdit || mutable.value.pending != null || delta == 0 || reason.isBlank() || reason.length > 200) return
        run(saving = true) { applyStock(inventory.adjust(productId, delta, reason)) }
    }

    fun recover() = run(saving = true) { applyStock(inventory.recover()) }

    private fun run(saving: Boolean, action: suspend () -> Unit) {
        val current = mutable.value
        if (current.busy) return
        mutable.value = current.copy(loading = !saving, saving = saving, error = null, problem = null, notice = null)
        viewModelScope.launch {
            try {
                action()
            } catch (failure: ApiFailure) {
                mutable.value = mutable.value.copy(pending = inventory.pending(), error = failure.display())
            } catch (_: IOException) {
                val message = if (mutable.value.verifyMenu && mutable.value.notice != R.string.staff_stock_saved) {
                    R.string.staff_menu_unknown
                } else {
                    R.string.staff_read_failed
                }
                mutable.value = mutable.value.copy(problem = message)
            } finally {
                mutable.value = mutable.value.copy(loading = false, saving = false)
            }
        }
    }

    private suspend fun readMenu() {
        mutable.value = mutable.value.copy(pending = inventory.pending())
        val menu = inventory.menu()
        mutable.value = mutable.value.copy(items = menu, verifyMenu = false)
    }

    private suspend fun verify(productId: String, patch: StaffMenuPatch) {
        mutable.value = mutable.value.copy(verifyMenu = true)
        readMenu()
        val updated = mutable.value.items?.firstOrNull { it.productId == productId }
        val notice = if (updated != null && patch.matches(updated)) R.string.staff_menu_saved else R.string.staff_menu_not_applied
        mutable.value = mutable.value.copy(notice = notice)
    }

    private suspend fun applyStock(outcome: StockOutcome) {
        mutable.value = mutable.value.copy(pending = inventory.pending())
        if (outcome == StockOutcome.PENDING) return
        if (outcome == StockOutcome.CONFIRMED) {
            mutable.value = mutable.value.copy(notice = R.string.staff_stock_saved, verifyMenu = true)
        }
        readMenu()
    }
}

private val pricePattern = Regex("[0-9]{1,19}([.][0-9]{1,2})?")

internal fun parsePriceCents(text: String): Long? {
    val value = text.trim()
    if (!pricePattern.matches(value)) return null
    return try {
        value.toBigDecimal().movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}
