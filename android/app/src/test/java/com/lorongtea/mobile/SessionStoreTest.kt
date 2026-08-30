package com.lorongtea.mobile

import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.SessionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
    @Test
    fun unauthorizedResponseFromThePreviousAccountLeavesTheNewSessionActive() {
        val sessions = SessionStore()
        val previous = Session("1", Role.CUSTOMER, "old")
        val current = Session("2", Role.STAFF, "new")
        sessions.start(previous)
        sessions.signOut()
        sessions.start(current)

        sessions.expire(previous)
        assertSame(current, sessions.state.value.account)
        assertFalse(sessions.state.value.expired)

        sessions.expire(current)
        assertNull(sessions.state.value.account)
        assertTrue(sessions.state.value.expired)
    }
}
