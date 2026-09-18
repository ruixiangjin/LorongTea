package com.lorongtea.mobile.data

internal enum class OrderAdvance { START, READY }

internal interface StaffOrderSource {
    suspend fun detail(orderId: String): Order
    suspend fun advance(orderId: String, action: OrderAdvance): Order
}

internal class StaffOrderRepository(private val backend: Backend) {
    suspend fun queue(account: Session, storeId: String, status: OrderStatus, limit: Int, offset: Int): List<Order> =
        backend.request(read = true, session = account) {
            staffOrders(account.authorization, storeId, status, limit, offset).data
        }

    fun forStore(account: Session, storeId: String): StaffOrderSource = object : StaffOrderSource {
        override suspend fun detail(orderId: String): Order = backend.request(read = true, session = account) {
            staffOrder(account.authorization, storeId, orderId).data
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
