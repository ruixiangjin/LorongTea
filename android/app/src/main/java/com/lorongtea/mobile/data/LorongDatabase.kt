package com.lorongtea.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cart_line", primaryKeys = ["accountId", "storeId", "productId"])
data class CartLine(
    val accountId: String,
    val storeId: String,
    val productId: String,
    val name: String,
    val priceCents: Long,
    val quantity: Int,
)

@Entity(tableName = "pending_checkout", primaryKeys = ["accountId", "storeId"])
data class PendingCheckout(
    val accountId: String,
    val storeId: String,
    val key: String,
    val body: String,
    val createdAt: Long,
    val priceChanged: Boolean = false,
)

@Dao
internal interface CartDao {
    @Query("SELECT * FROM cart_line WHERE accountId = :accountId AND storeId = :storeId ORDER BY productId")
    fun observe(accountId: String, storeId: String): Flow<List<CartLine>>

    @Query("SELECT * FROM cart_line WHERE accountId = :accountId AND storeId = :storeId AND productId = :productId")
    suspend fun find(accountId: String, storeId: String, productId: String): CartLine?

    @Upsert
    suspend fun save(line: CartLine)

    @Query("DELETE FROM cart_line WHERE accountId = :accountId AND storeId = :storeId AND productId = :productId")
    suspend fun remove(accountId: String, storeId: String, productId: String)
    @Query("SELECT * FROM cart_line WHERE accountId = :accountId AND storeId = :storeId ORDER BY productId")
    suspend fun snapshot(accountId: String, storeId: String): List<CartLine>

    @Query("DELETE FROM cart_line WHERE accountId = :accountId AND storeId = :storeId")
    suspend fun clear(accountId: String, storeId: String)
}

@Dao
internal interface PendingCheckoutDao {
    @Query("SELECT * FROM pending_checkout WHERE accountId = :accountId AND storeId = :storeId")
    fun observe(accountId: String, storeId: String): Flow<PendingCheckout?>

    @Query("SELECT * FROM pending_checkout WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeAccount(accountId: String): Flow<List<PendingCheckout>>

    @Query("SELECT * FROM pending_checkout WHERE accountId = :accountId AND storeId = :storeId")
    suspend fun find(accountId: String, storeId: String): PendingCheckout?

    @Insert
    suspend fun insert(checkout: PendingCheckout)

    @Query("DELETE FROM pending_checkout WHERE accountId = :accountId AND storeId = :storeId AND `key` = :key")
    suspend fun delete(accountId: String, storeId: String, key: String): Int

    @Query("UPDATE pending_checkout SET priceChanged = 1 WHERE accountId = :accountId AND storeId = :storeId AND `key` = :key")
    suspend fun markPriceChanged(accountId: String, storeId: String, key: String)
}

@Database(entities = [CartLine::class, PendingCheckout::class], version = 1)
abstract class LorongDatabase : RoomDatabase() {
    internal abstract fun cart(): CartDao
    internal abstract fun pending(): PendingCheckoutDao
}
