package com.lorongtea.mobile

import android.app.Application
import androidx.room.Room
import com.lorongtea.mobile.data.Backend
import com.lorongtea.mobile.data.CampaignRepository
import com.lorongtea.mobile.data.CatalogRepository
import com.lorongtea.mobile.data.CartRepository
import com.lorongtea.mobile.data.CheckoutRepository
import com.lorongtea.mobile.data.LorongDatabase
import com.lorongtea.mobile.data.MIGRATION_1_2
import com.lorongtea.mobile.data.MIGRATION_2_3
import com.lorongtea.mobile.data.StaffInventoryRepository
import com.lorongtea.mobile.data.OrderRepository
import com.lorongtea.mobile.data.SessionRepository
import com.lorongtea.mobile.data.SessionStore
import com.lorongtea.mobile.data.StaffOrderRepository

class LorongTeaApplication : Application() {
    val sessions = SessionStore()
    private val backend by lazy { Backend(BuildConfig.API_URL, sessions) }
    val accounts by lazy { SessionRepository(backend, sessions) }
    val catalog by lazy { CatalogRepository(backend) }

    private val database by lazy {
        Room.databaseBuilder(this, LorongDatabase::class.java, "lorong-tea.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    internal val campaigns by lazy { CampaignRepository(backend, database.pendingCampaign()) }
    val carts by lazy { CartRepository(database) }
    val orders by lazy { OrderRepository(backend) }
    internal val staffOrders by lazy { StaffOrderRepository(backend) }
    internal val staffInventory by lazy { StaffInventoryRepository(backend, database.pendingStock()) }
    val checkout by lazy { CheckoutRepository(backend, carts, catalog) }
}
