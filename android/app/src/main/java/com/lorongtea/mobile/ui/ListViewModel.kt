package com.lorongtea.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ListState<T>(val items: List<T>? = null, val loading: Boolean = true, val error: String? = null)

class ListViewModel<T>(private val load: suspend () -> List<T>) : ViewModel() {
    private val mutable = MutableStateFlow(ListState<T>())
    val state = mutable.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                mutable.value = ListState(items = load(), loading = false)
            } catch (error: ApiFailure) {
                mutable.value = mutable.value.copy(loading = false, error = error.display())
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(loading = false, error = "Connection unavailable. Try refreshing.")
            }
        }
    }
}

internal fun ApiFailure.display(): String = if (requestId == null) {
    message.orEmpty()
} else {
    "$message (request $requestId)"
}
