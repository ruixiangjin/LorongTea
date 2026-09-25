package com.lorongtea.mobile.data

import java.io.IOException
import java.util.UUID

internal data class StaffMenuItem(
    val productId: String,
    val name: String,
    val priceCents: Long,
    val listed: Boolean,
    val availableQty: Int,
)

internal data class StaffMenuPatch(val priceCents: Long?, val listed: Boolean?) {
    val hasChanges get() = priceCents != null || listed != null
    fun matches(item: StaffMenuItem): Boolean =
        (priceCents == null || priceCents == item.priceCents) && (listed == null || listed == item.listed)
}

internal data class StockAdjustmentRequest(
    val operationId: String,
    val productId: String,
    val delta: Int,
    val reason: String,
)

internal data class StockAdjustmentResult(val operationId: String, val productId: String)

internal enum class StockOutcome { CONFIRMED, PENDING, MISSING }

internal class StaffInventoryRepository(
    private val backend: Backend,
    private val pending: PendingStockDao,
) {
    fun forStore(account: Session, storeId: String): StaffInventory = StaffInventory(account, storeId, backend, pending)
}

internal class StaffInventory(
    private val account: Session,
    private val storeId: String,
    private val backend: Backend,
    private val dao: PendingStockDao,
) {
    suspend fun menu(): List<StaffMenuItem> = backend.request(read = true, session = account) {
        staffMenu(account.authorization, storeId).data
    }

    suspend fun update(productId: String, patch: StaffMenuPatch): StaffMenuItem =
        backend.request(read = false, session = account) {
            updateStaffMenu(account.authorization, storeId, productId, patch).data
        }

    suspend fun pending(): PendingStockAdjustment? = dao.find(account.userId, storeId)

    suspend fun adjust(productId: String, delta: Int, reason: String): StockOutcome {
        val adjustment = PendingStockAdjustment(account.userId, storeId, UUID.randomUUID().toString(), productId, delta, reason)
        if (dao.insert(adjustment) == -1L) return StockOutcome.PENDING
        return send(adjustment)
    }

    suspend fun recover(): StockOutcome {
        val adjustment = pending() ?: return StockOutcome.MISSING
        return send(adjustment)
    }

    private suspend fun send(adjustment: PendingStockAdjustment): StockOutcome {
        val request = StockAdjustmentRequest(adjustment.operationId, adjustment.productId, adjustment.delta, adjustment.reason)
        val result = try {
            backend.request(read = false, session = account) { adjustStock(account.authorization, storeId, request).data }
        } catch (failure: ApiFailure) {
            if (!failure.isDefinitiveRejection()) return StockOutcome.PENDING
            dao.delete(account.userId, storeId, adjustment.operationId)
            throw failure
        } catch (_: IOException) {
            return StockOutcome.PENDING
        }
        if (result.operationId != adjustment.operationId || result.productId != adjustment.productId) {
            return StockOutcome.PENDING
        }
        dao.delete(account.userId, storeId, adjustment.operationId)
        return StockOutcome.CONFIRMED
    }
}

internal fun ApiFailure.isDefinitiveRejection(): Boolean =
    status in 400..499 && status !in listOf(401, 408, 425, 429)
