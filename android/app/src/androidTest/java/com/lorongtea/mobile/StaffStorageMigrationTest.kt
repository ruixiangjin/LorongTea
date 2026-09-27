package com.lorongtea.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lorongtea.mobile.data.LorongDatabase
import com.lorongtea.mobile.data.MIGRATION_1_2
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaffStorageMigrationTest {
    @Test
    fun inventoryMigrationPreservesExistingCustomerCartAndPendingCheckout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "stock-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        val schema = instrumentation.context.assets
            .open("com.lorongtea.mobile.data.LorongDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        try {
            path.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
                old.version = schema.getInt("version")
                old.execSQL("""
                    INSERT INTO cart_line (accountId, storeId, productId, name, priceCents, quantity)
                    VALUES ('customer','1','9','Tea',1090,2)
                """.trimIndent())
                old.execSQL("""
                    INSERT INTO pending_checkout (accountId, storeId, key, body, createdAt, priceChanged)
                    VALUES ('customer','1','original-order-key','{}',100,0)
                """.trimIndent())
            }
            val room = Room.databaseBuilder(context, LorongDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
            try {
                val migrated = room.openHelper.writableDatabase
                assertEquals(2, migrated.version)
                migrated.query("SELECT quantity FROM cart_line").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(2, it.getInt(0))
                }
                migrated.query("SELECT key FROM pending_checkout").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("original-order-key", it.getString(0))
                }
                migrated.execSQL("""
                    INSERT INTO pending_stock_adjustment (accountId, storeId, operationId, productId, delta, reason)
                    VALUES ('staff','1','original-stock-operation','9',-2,'Damaged packaging')
                """.trimIndent())
                migrated.query("SELECT operationId FROM pending_stock_adjustment").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("original-stock-operation", it.getString(0))
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
