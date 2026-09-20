package com.lorongtea.mobile.data

internal enum class OrderAdvance { START, READY }
internal enum class PaymentConfirmationResult { CONFIRMED, ALREADY_PAID }

internal data class PaymentConfirmation(
    val result: PaymentConfirmationResult,
    val orderId: String,
    val status: OrderStatus,
    val paymentStatus: PaymentStatus,
    val totalCents: Long,
)

internal enum class PickupRedemptionResult { REDEEMED, ALREADY_REDEEMED }

internal data class PickupRequest(val pickupToken: String)
internal data class PickupRedemption(val result: PickupRedemptionResult, val orderId: String)

internal interface StaffOrderSource {
    suspend fun detail(orderId: String): Order
    suspend fun advance(orderId: String, action: OrderAdvance): Order
    suspend fun confirmPayment(orderId: String): PaymentConfirmation
}

internal class StaffOrderRepository(private val backend: Backend) {
    suspend fun queue(account: Session, storeId: String, status: OrderStatus, limit: Int, offset: Int): List<Order> =
        backend.request(read = true, session = account) {
            staffOrders(account.authorization, storeId, status, limit, offset).data
        }

    suspend fun redeem(account: Session, storeId: String, code: String): PickupRedemption =
        backend.request(read = false, session = account) {
            redeemPickup(account.authorization, storeId, PickupRequest(code)).data
        }

    fun forStore(account: Session, storeId: String): StaffOrderSource = object : StaffOrderSource {
        override suspend fun detail(orderId: String): Order = backend.request(read = true, session = account) {
            staffOrder(account.authorization, storeId, orderId).data
        }

        override suspend fun confirmPayment(orderId: String): PaymentConfirmation =
            backend.request(read = false, session = account) {
                confirmOrderPayment(account.authorization, storeId, orderId).data
            }

        override suspend fun advance(orderId: String, action: OrderAdvance): Order =
            backend.request(read = false, session = account) {
                when (action) {
                    OrderAdvance.START -> startOrder(account.authorization, storeId, orderId).data
                    OrderAdvance.READY -> markOrderReady(account.authorization, storeId, orderId).data
                }
            }
    }
}
