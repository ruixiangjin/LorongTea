package com.lorongtea.mobile

import com.google.gson.JsonParser
import com.lorongtea.mobile.data.Backend
import com.lorongtea.mobile.data.OrderAdvance
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentConfirmationResult
import com.lorongtea.mobile.data.PendingStockAdjustment
import com.lorongtea.mobile.data.PendingStockDao
import com.lorongtea.mobile.data.PickupRedemptionResult
import com.lorongtea.mobile.data.Role
import com.lorongtea.mobile.data.Session
import com.lorongtea.mobile.data.SessionStore
import com.lorongtea.mobile.data.StaffInventoryRepository
import com.lorongtea.mobile.data.StaffMenuPatch
import com.lorongtea.mobile.data.StaffOrderRepository
import com.lorongtea.mobile.data.StockOutcome
import com.lorongtea.mobile.ui.staff.parsePriceCents
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StaffApiTest {
    private val account = Session("staff", Role.STAFF, "test-token")

    @Test
    fun orderQueueAndFulfillmentUseAssignedStoreAndBearerSession() = runBlocking {
        MockWebServer().use { server ->
            val repository = StaffOrderRepository(backend(server))
            val orders = repository.forStore(account, "3")
            server.enqueue(reply("[]"))
            assertEquals(emptyList<Any>(), repository.queue(account, "3", OrderStatus.CREATED, 20, 40))
            val list = server.request()
            assertEquals("/api/v1/staff/stores/3/orders?status=CREATED&limit=20&offset=40", list.path)
            assertEquals("Bearer test-token", list.getHeader("Authorization"))

            server.enqueue(reply(order("PREPARING")))
            assertEquals(OrderStatus.PREPARING, orders.advance("7", OrderAdvance.START).status)
            assertEquals("/api/v1/staff/stores/3/orders/7/start", server.request().path)
            server.enqueue(reply(order("READY")))
            assertEquals(OrderStatus.READY, orders.advance("7", OrderAdvance.READY).status)
            assertEquals("/api/v1/staff/stores/3/orders/7/ready", server.request().path)

            server.enqueue(reply("""{"result":"ALREADY_PAID","orderId":"7","status":"READY","paymentStatus":"PAID","totalCents":1090}"""))
            assertEquals(PaymentConfirmationResult.ALREADY_PAID, orders.confirmPayment("7").result)
            assertEquals("/api/v1/staff/stores/3/orders/7/confirm-payment", server.request().path)

            val code = "AbCdEfGhIjKlMnOpQrSt_-"
            server.enqueue(reply("""{"result":"REDEEMED","orderId":"7"}"""))
            assertEquals(PickupRedemptionResult.REDEEMED, repository.redeem(account, "3", code).result)
            val pickup = server.request()
            assertEquals("/api/v1/staff/stores/3/orders/redeem", pickup.path)
            assertEquals(code, JsonParser.parseString(pickup.body.readUtf8()).asJsonObject["pickupToken"].asString)
        }
    }

    @Test
    fun menuPatchOmitsUntouchedFields() = runBlocking {
        MockWebServer().use { server ->
            val inventory = StaffInventoryRepository(backend(server), InMemoryPendingStock()).forStore(account, "3")
            server.enqueue(reply("""{"productId":"9","name":"Tea","priceCents":1190,"listed":true,"availableQty":7}"""))
            assertEquals(1190L, inventory.update("9", StaffMenuPatch(1190, null)).priceCents)
            val request = server.request()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/staff/stores/3/menu-items/9", request.path)
            val patch = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(1190L, patch["priceCents"].asLong)
            assertFalse(patch.has("listed"))
        }
    }

    @Test
    fun stockRetrySurvivesRepositoryRecreationAndUsesTheSameOperation() = runBlocking {
        MockWebServer().use { server ->
            val dao = InMemoryPendingStock()
            val backend = backend(server)
            val inventory = StaffInventoryRepository(backend, dao).forStore(account, "3")
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertEquals(StockOutcome.PENDING, inventory.adjust("9", -2, "Damaged packaging"))
            val first = server.request().body.readUtf8()
            val saved = requireNotNull(dao.find("staff", "3"))
            assertNotNull(saved.operationId)

            val restarted = StaffInventoryRepository(backend(server), dao).forStore(account, "3")
            assertEquals(StockOutcome.PENDING, restarted.adjust("9", 1, "Another adjustment"))
            assertEquals(StockOutcome.MISSING, StaffInventoryRepository(backend, dao).forStore(account, "4").recover())
            assertEquals(1, server.requestCount)

            server.enqueue(reply("""{"operationId":"${saved.operationId}","productId":"9"}"""))
            assertEquals(StockOutcome.CONFIRMED, restarted.recover())
            assertEquals(first, server.request().body.readUtf8())
            assertNull(dao.find("staff", "3"))
        }
    }

    @Test
    fun parsesRinggitWithoutRoundingOrAcceptingNegativePrices() {
        assertEquals(1090L, parsePriceCents("10.90"))
        assertEquals(1050L, parsePriceCents("10.5"))
        assertEquals(0L, parsePriceCents("0"))
        assertNull(parsePriceCents("10.005"))
        assertNull(parsePriceCents("-1"))
        assertNull(parsePriceCents("9999999999999999999.99"))
    }

    private fun backend(server: MockWebServer) = Backend(server.url("/").toString(), SessionStore())
    private fun MockWebServer.request(): RecordedRequest = requireNotNull(takeRequest(2, TimeUnit.SECONDS))
    private fun reply(data: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":$data}""")
    private fun order(status: String) =
        """{"id":"7","storeId":"3","status":"$status","paymentStatus":"UNPAID","totalCents":1090}"""
}

private class InMemoryPendingStock : PendingStockDao {
    private val operations = mutableMapOf<Pair<String, String>, PendingStockAdjustment>()

    override suspend fun find(accountId: String, storeId: String): PendingStockAdjustment? =
        operations[accountId to storeId]

    override suspend fun insert(adjustment: PendingStockAdjustment): Long =
        if (operations.putIfAbsent(adjustment.accountId to adjustment.storeId, adjustment) == null) 1L else -1L

    override suspend fun delete(accountId: String, storeId: String, operationId: String) {
        if (find(accountId, storeId)?.operationId == operationId) operations.remove(accountId to storeId)
    }
}
