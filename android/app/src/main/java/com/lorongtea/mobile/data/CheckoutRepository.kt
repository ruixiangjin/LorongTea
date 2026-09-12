package com.lorongtea.mobile.data

import java.io.IOException
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

internal data class PlacedOrder(val id: String)
private data class CheckoutItem(val productId: String, val quantity: Int)
private data class CheckoutBody(val storeId: String, val items: List<CheckoutItem>, val expectedTotalCents: Long)

sealed interface CheckoutOutcome {
    data class Confirmed(val orderId: String) : CheckoutOutcome
    data class Notice(val message: String) : CheckoutOutcome
    data class Repriced(val update: CartPriceUpdate) : CheckoutOutcome
    data object AwaitingConfirmation : CheckoutOutcome
    data object RefreshPrices : CheckoutOutcome
}

class CheckoutRepository(
    private val backend: Backend,
    private val carts: CartRepository,
    private val catalog: CatalogRepository,
) {
    suspend fun confirm(account: Session, storeId: String, reviewed: List<CartLine>): CheckoutOutcome {
        val total = reviewed.totalCents()
        if (reviewed.isEmpty() || total == null) throw CartProblem("Review the items and total before placing an order.")
        val body = CheckoutBody(storeId, reviewed.map { CheckoutItem(it.productId, it.quantity) }, total)
        val pending = PendingCheckout(account.userId, storeId, UUID.randomUUID().toString(),
            backend.gson.toJson(body), System.currentTimeMillis())
        carts.prepare(pending, reviewed)
        return send(account, pending)
    }

    suspend fun recover(account: Session, storeId: String): CheckoutOutcome {
        val pending = carts.pending(account.userId, storeId)
            ?: return CheckoutOutcome.Notice("There is no order awaiting confirmation for this store.")
        return if (pending.priceChanged) reprice(pending) else send(account, pending)
    }

    private suspend fun send(account: Session, pending: PendingCheckout): CheckoutOutcome {
        val order = try {
            backend.request(read = false, session = account) {
                placeOrder(account.authorization, pending.key, pending.body.toRequestBody(JSON)).data
            }
        } catch (error: ApiFailure) {
            if (error.code == "PRICE_CHANGED") {
                carts.markPriceChanged(pending)
                return reprice(pending)
            }
            if (error.status in 400..499 && error.status !in listOf(401, 408, 425, 429)) {
                carts.reject(pending)
                return CheckoutOutcome.Notice(error.message.orEmpty())
            }
            return CheckoutOutcome.AwaitingConfirmation
        } catch (_: IOException) {
            return CheckoutOutcome.AwaitingConfirmation
        }

        carts.complete(pending)
        return CheckoutOutcome.Confirmed(order.id)
    }

    private suspend fun reprice(pending: PendingCheckout): CheckoutOutcome {
        val menu = try {
            catalog.menu(pending.storeId)
        } catch (_: ApiFailure) {
            return CheckoutOutcome.RefreshPrices
        } catch (_: IOException) {
            return CheckoutOutcome.RefreshPrices
        }
        val update = carts.reprice(pending, menu)
            ?: return CheckoutOutcome.Notice("There is no order awaiting confirmation for this store.")
        return CheckoutOutcome.Repriced(update)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
