package com.lorongtea.mobile

import android.app.Application
import androidx.room.Room
import com.lorongtea.mobile.data.Backend
import com.lorongtea.mobile.data.CatalogRepository
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.LorongDatabase
import com.lorongtea.mobile.data.SessionRepository
import com.lorongtea.mobile.data.SessionStore

class LorongTeaApplication : Application() {
    val sessions = SessionStore()
    private val backend by lazy { Backend(BuildConfig.API_URL, sessions) }
    val accounts by lazy { SessionRepository(backend, sessions) }
    val catalog by lazy { CatalogRepository(backend) }

    private val database by lazy {
        Room.databaseBuilder(this, LorongDatabase::class.java, "lorong-tea.db").build()
    }
    val carts by lazy { CartRepository(database) }
}
