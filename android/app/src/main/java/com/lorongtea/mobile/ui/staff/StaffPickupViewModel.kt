package com.lorongtea.mobile.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.PickupRedemption
import com.lorongtea.mobile.ui.common.display
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val pickupPattern = Regex("[A-Za-z0-9_-]{22}")

internal data class StaffPickupState(
    val code: String = "",
    val submitting: Boolean = false,
    val result: PickupRedemption? = null,
    val problem: Int? = null,
    val error: String? = null,
) {
    val canSubmit get() = !submitting && result == null && code.isNotBlank()
}

internal class StaffPickupViewModel(
    private val submit: suspend (String) -> PickupRedemption,
) : ViewModel() {
    private val mutable = MutableStateFlow(StaffPickupState())
    val state = mutable.asStateFlow()

    fun edit(code: String) {
        if (mutable.value.submitting || mutable.value.result != null) return
        mutable.value = StaffPickupState(code = code)
    }

    fun reset() {
        if (!mutable.value.submitting) mutable.value = StaffPickupState()
    }

    fun redeem() {
        val current = mutable.value
        if (!current.canSubmit) return
        val code = current.code.trim()
        if (!pickupPattern.matches(code)) {
            mutable.value = current.copy(problem = R.string.staff_pickup_format, error = null)
            return
        }
        mutable.value = StaffPickupState(code = code, submitting = true)
        viewModelScope.launch {
            mutable.value = try {
                StaffPickupState(result = submit(code))
            } catch (failure: ApiFailure) {
                when {
                    failure.code == "PAYMENT_REQUIRED" -> StaffPickupState(code, problem = R.string.staff_pickup_unpaid)
                    failure.status == 404 -> StaffPickupState(code, problem = R.string.staff_pickup_invalid)
                    failure.status >= 500 || failure.status in listOf(408, 425, 429) ->
                        StaffPickupState(code, problem = R.string.staff_pickup_unknown)
                    else -> StaffPickupState(code, error = failure.display())
                }
            } catch (_: IOException) {
                StaffPickupState(code, problem = R.string.staff_pickup_unknown)
            }
        }
    }
}
