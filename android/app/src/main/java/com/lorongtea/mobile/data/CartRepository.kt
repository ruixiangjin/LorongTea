package com.lorongtea.mobile.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class CartProblem(message: String) : Exception(message)

data class CartPriceUpdate(
    val previousTotal: Long?,
    val updatedTotal: Long?,
    val unavailableProducts: Set<String>,
)

class CartRepository(private val database: LorongDatabase) {
    private val dao = database.cart()
    private val pending = database.pending()

    fun observe(accountId: String, storeId: String): Flow<List<CartLine>> = dao.observe(accountId, storeId)

    suspend fun add(accountId: String, storeId: String, item: MenuItem) = database.withTransaction {
        requireEditable(accountId, storeId)
        if (!item.listed) throw CartProblem("This item is unavailable.")
        val previous = dao.find(accountId, storeId, item.productId)
        val quantity = previous?.quantity ?: 0
        if (quantity == Int.MAX_VALUE) throw CartProblem("This item has reached the quantity limit.")
        dao.save(CartLine(accountId, storeId, item.productId, item.name, item.priceCents, quantity + 1))
    }

    suspend fun change(accountId: String, storeId: String, productId: String, delta: Int) = database.withTransaction {
        requireEditable(accountId, storeId)
        val line = dao.find(accountId, storeId, productId) ?: return@withTransaction
        val quantity = line.quantity.toLong() + delta
        when {
            quantity <= 0 -> dao.remove(accountId, storeId, productId)
            quantity > Int.MAX_VALUE -> throw CartProblem("This item has reached the quantity limit.")
            else -> dao.save(line.copy(quantity = quantity.toInt()))
        }
    }

    fun observePending(accountId: String, storeId: String): Flow<PendingCheckout?> = pending.observe(accountId, storeId)

    fun observePending(accountId: String): Flow<List<PendingCheckout>> = pending.observeAccount(accountId)

    internal suspend fun pending(accountId: String, storeId: String): PendingCheckout? = pending.find(accountId, storeId)

    internal suspend fun prepare(checkout: PendingCheckout, reviewed: List<CartLine>) = database.withTransaction {
        requireEditable(checkout.accountId, checkout.storeId)
        if (dao.snapshot(checkout.accountId, checkout.storeId) != reviewed) {
            throw CartProblem("Your cart changed. Review the current total and try again.")
        }
        pending.insert(checkout)
    }

    internal suspend fun complete(checkout: PendingCheckout): Unit = database.withTransaction {
        if (pending.delete(checkout.accountId, checkout.storeId, checkout.key) == 1) {
            dao.clear(checkout.accountId, checkout.storeId)
        }
    }

    internal suspend fun reject(checkout: PendingCheckout) {
        pending.delete(checkout.accountId, checkout.storeId, checkout.key)
    }

    internal suspend fun markPriceChanged(checkout: PendingCheckout) {
        pending.markPriceChanged(checkout.accountId, checkout.storeId, checkout.key)
    }

    internal suspend fun reprice(pending: PendingCheckout, menu: List<MenuItem>): CartPriceUpdate? =
        database.withTransaction {
            if (this.pending.delete(pending.accountId, pending.storeId, pending.key) == 0) {
                return@withTransaction null
            }
            val original = dao.snapshot(pending.accountId, pending.storeId)
            val plan = planCartReprice(original, menu)
            plan.changedLines.forEach { dao.save(it) }
            plan.comparison
        }

    private suspend fun requireEditable(accountId: String, storeId: String) {
        if (pending.find(accountId, storeId) != null) {
            throw CartProblem("Finish confirming the previous order before changing this cart.")
        }
    }

}

internal data class CartRepricePlan(val changedLines: List<CartLine>, val comparison: CartPriceUpdate)

internal fun planCartReprice(original: List<CartLine>, menu: List<MenuItem>): CartRepricePlan {
    val available = menu.filter { it.listed }.associateBy { it.productId }
    val unavailable = original.mapNotNull { it.productId.takeUnless(available::containsKey) }.toSet()
    val updated = original.map { line ->
        val item = available[line.productId]
        if (item == null) line else line.copy(name = item.name, priceCents = item.priceCents)
    }
    return CartRepricePlan(
        changedLines = updated.filterIndexed { index, line -> line != original[index] },
        comparison = CartPriceUpdate(
            previousTotal = original.totalCents(),
            updatedTotal = updated.totalCents().takeIf { unavailable.isEmpty() },
            unavailableProducts = unavailable,
        ),
    )
}

internal fun List<CartLine>.totalCents(): Long? {
    var total = 0L
    for (line in this) {
        if (line.priceCents > (Long.MAX_VALUE - total) / line.quantity) return null
        total += line.priceCents * line.quantity
    }
    return total
}
