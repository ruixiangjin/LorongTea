package com.lorongtea.mobile.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.ui.common.display
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class StaffQueue(
    val filter: OrderStatus = OrderStatus.CREATED,
    val orders: List<Order>? = null,
    val loading: Boolean = true,
    val hasMore: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

internal class StaffQueueViewModel(
    private val loadPage: suspend (OrderStatus, Int, Int) -> List<Order>,
) : ViewModel() {
    private val mutable = MutableStateFlow(StaffQueue())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var offset = 0

    fun select(filter: OrderStatus) {
        if (filter == mutable.value.filter) return
        request?.cancel()
        offset = 0
        load(StaffQueue(filter = filter), 0)
    }

    fun refresh() {
        if (request?.isActive == true) return
        load(mutable.value, 0)
    }

    fun more() {
        val current = mutable.value
        if (request?.isActive == true || !current.hasMore) return
        load(current, offset)
    }

    private fun load(previous: StaffQueue, start: Int) {
        mutable.value = previous.copy(loading = true, error = null, offline = false)
        request = viewModelScope.launch {
            try {
                val page = loadPage(previous.filter, PAGE_SIZE, start)
                ensureActive()
                offset = start + page.size
                val orders = if (start == 0) page else (previous.orders.orEmpty() + page).distinctBy { it.id }
                mutable.value = StaffQueue(previous.filter, orders, loading = false, hasMore = page.size == PAGE_SIZE)
            } catch (failure: ApiFailure) {
                ensureActive()
                mutable.value = previous.copy(loading = false, error = failure.display(), offline = false)
            } catch (_: IOException) {
                ensureActive()
                mutable.value = previous.copy(loading = false, error = null, offline = true)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
