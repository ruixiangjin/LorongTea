package com.lorongtea.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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
}

@Database(entities = [CartLine::class], version = 1)
abstract class LorongDatabase : RoomDatabase() {
    internal abstract fun cart(): CartDao
}
