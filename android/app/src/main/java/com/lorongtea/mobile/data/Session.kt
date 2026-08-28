package com.lorongtea.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Role { CUSTOMER, STAFF }

class Session internal constructor(val userId: String, val role: Role, private val token: String) {
    internal val authorization: String get() = "Bearer $token"
}

data class SessionState(val account: Session? = null, val expired: Boolean = false)

class SessionStore {
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()

    @Synchronized
    internal fun start(account: Session) {
        mutable.value = SessionState(account)
    }

    @Synchronized
    internal fun expire(account: Session) {
        if (mutable.value.account === account) mutable.value = SessionState(expired = true)
    }

    @Synchronized
    fun signOut() {
        mutable.value = SessionState()
    }
}

internal data class Credentials(val username: String, val password: String)
internal data class LoginResult(val userId: String, val role: Role, val token: String)

class SessionRepository(private val backend: Backend, private val sessions: SessionStore) {
    suspend fun signIn(username: String, password: String) {
        val result = backend.request(read = false) { login(Credentials(username, password)).data }
        sessions.start(Session(result.userId, result.role, result.token))
    }

    suspend fun assignedStore(account: Session): Store = backend.request(read = true, session = account) {
        assignedStore(account.authorization).data
    }
}
