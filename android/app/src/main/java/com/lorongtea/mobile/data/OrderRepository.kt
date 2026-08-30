package com.lorongtea.mobile.data

enum class OrderStatus { CREATED, PREPARING, READY, COMPLETED, CANCELLED }
enum class PaymentStatus { UNPAID, PAID }

data class OrderLine(val productId: String, val productName: String, val unitPriceCents: Long, val quantity: Int)

data class Order(
    val id: String,
    val storeId: String,
    val status: OrderStatus,
    val paymentStatus: PaymentStatus,
    val totalCents: Long,
    val items: List<OrderLine>?,
    val pickupToken: String?,
)

class OrderRepository(private val backend: Backend) {
    suspend fun history(account: Session, limit: Int, offset: Int): List<Order> =
        backend.request(read = true, session = account) { orders(account.authorization, limit, offset).data }

    suspend fun detail(account: Session, orderId: String): Order =
        backend.request(read = true, session = account) { order(account.authorization, orderId).data }

    suspend fun cancel(account: Session, orderId: String): Order =
        backend.request(read = false, session = account) { cancel(account.authorization, orderId).data }
}
