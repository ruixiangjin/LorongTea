package com.lorongtea.mobile.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class CartProblem(message: String) : Exception(message)

class CartRepository(private val database: LorongDatabase) {
    private val dao = database.cart()

    fun observe(accountId: String, storeId: String): Flow<List<CartLine>> = dao.observe(accountId, storeId)

    suspend fun add(accountId: String, storeId: String, item: MenuItem) = database.withTransaction {
        if (!item.listed) throw CartProblem("This item is unavailable.")
        val previous = dao.find(accountId, storeId, item.productId)
        val quantity = previous?.quantity ?: 0
        if (quantity == Int.MAX_VALUE) throw CartProblem("This item has reached the quantity limit.")
        dao.save(CartLine(accountId, storeId, item.productId, item.name, item.priceCents, quantity + 1))
    }

    suspend fun change(accountId: String, storeId: String, productId: String, delta: Int) = database.withTransaction {
        val line = dao.find(accountId, storeId, productId) ?: return@withTransaction
        val quantity = line.quantity.toLong() + delta
        when {
            quantity <= 0 -> dao.remove(accountId, storeId, productId)
            quantity > Int.MAX_VALUE -> throw CartProblem("This item has reached the quantity limit.")
            else -> dao.save(line.copy(quantity = quantity.toInt()))
        }
    }
}

internal fun List<CartLine>.totalCents(): Long? {
    var total = 0L
    for (line in this) {
        if (line.priceCents > (Long.MAX_VALUE - total) / line.quantity) return null
        total += line.priceCents * line.quantity
    }
    return total
}
