package com.lorongtea.mobile.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderAdvance
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.StaffOrderSource
import com.lorongtea.mobile.ui.common.display
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class StaffOrderState(
    val order: Order? = null,
    val loading: Boolean = false,
    val updating: Boolean = false,
    val needsRefresh: Boolean = false,
    val message: Int? = null,
    val error: String? = null,
) {
    val busy get() = loading || updating
}

internal class StaffOrderViewModel(
    private val source: StaffOrderSource,
    private val orderId: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(StaffOrderState())
    val state = mutable.asStateFlow()

    fun refresh() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(loading = true, message = null, error = null)
        viewModelScope.launch { readOrder() }
    }

    fun advance() {
        val current = mutable.value
        if (current.busy || current.needsRefresh) return
        val action = when (current.order?.status) {
            OrderStatus.CREATED -> OrderAdvance.START
            OrderStatus.PREPARING -> OrderAdvance.READY
            else -> return
        }
        update {
            val updated = source.advance(orderId, action)
            mutable.value = mutable.value.copy(order = updated.copy(items = updated.items ?: current.order?.items))
        }
    }

    private fun update(action: suspend () -> Unit) {
        mutable.value = mutable.value.copy(updating = true, message = null, error = null)
        viewModelScope.launch {
            try {
                action()
            } catch (failure: ApiFailure) {
                mutable.value = mutable.value.copy(needsRefresh = true)
                if (failure.status != 401) readOrder(errorAfterRead = failure.display())
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(needsRefresh = true)
                readOrder()
            } finally {
                mutable.value = mutable.value.copy(updating = false)
            }
        }
    }

    private suspend fun readOrder(errorAfterRead: String? = null) {
        try {
            val order = source.detail(orderId)
            mutable.value = mutable.value.copy(order = order, needsRefresh = false, error = errorAfterRead)
        } catch (failure: ApiFailure) {
            mutable.value = mutable.value.copy(error = failure.display())
        } catch (_: IOException) {
            val message = if (mutable.value.needsRefresh) R.string.staff_update_unknown else R.string.staff_read_failed
            mutable.value = mutable.value.copy(message = message)
        } finally {
            mutable.value = mutable.value.copy(loading = false)
        }
    }
}
