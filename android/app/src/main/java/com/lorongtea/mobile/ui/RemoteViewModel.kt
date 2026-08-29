package com.lorongtea.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.data.ApiFailure
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteState<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)

class RemoteViewModel<T>(private val load: suspend () -> T) : ViewModel() {
    private val mutable = MutableStateFlow(RemoteState<T>())
    val state = mutable.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            mutable.value = loadRemote(mutable.value, load)
        }
    }
}

internal suspend fun <T> loadRemote(previous: RemoteState<T>, load: suspend () -> T): RemoteState<T> = try {
    RemoteState(data = load(), loading = false)
} catch (error: ApiFailure) {
    previous.copy(loading = false, error = error.display())
} catch (_: IOException) {
    previous.copy(loading = false, error = "Connection unavailable. Try refreshing.")
}

internal fun ApiFailure.display(): String = if (requestId == null) {
    message.orEmpty()
} else {
    "$message (request $requestId)"
}
