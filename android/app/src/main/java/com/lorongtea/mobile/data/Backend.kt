package com.lorongtea.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class Store(val id: String, val code: String, val name: String)

internal data class Envelope<T>(val data: T)
internal data class ErrorBody(val code: String?, val message: String?, val requestId: String?)

internal interface LorongTeaApi {
    @GET("api/v1/stores")
    suspend fun stores(): Envelope<List<Store>>

    @GET("api/v1/stores/{storeId}/menu")
    suspend fun menu(@Path("storeId") storeId: String): Envelope<List<MenuItem>>

    @GET("api/v1/stores/{storeId}/campaigns")
    suspend fun campaigns(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
    ): Envelope<List<CampaignDto>>

    @POST("api/v1/stores/{storeId}/campaigns/{campaignId}/claims")
    suspend fun claimCampaign(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("campaignId") campaignId: String,
        @Body request: ClaimRequest,
    ): Envelope<ClaimReceipt>

    @GET("api/v1/promotion-claims/{claimId}")
    suspend fun claimReceipt(
        @Header("Authorization") authorization: String,
        @Path("claimId") claimId: String,
    ): Envelope<ClaimReceipt>

    @POST("api/v1/auth/login")
    suspend fun login(@Body credentials: Credentials): Envelope<LoginResult>

    @GET("api/v1/staff/store")
    suspend fun assignedStore(@Header("Authorization") authorization: String): Envelope<Store>

    @POST("api/v1/orders")
    suspend fun placeOrder(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") key: String,
        @Body request: RequestBody,
    ): Envelope<PlacedOrder>

    @GET("api/v1/orders")
    suspend fun orders(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Envelope<List<Order>>

    @GET("api/v1/orders/{orderId}")
    suspend fun order(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String,
    ): Envelope<Order>

    @POST("api/v1/orders/{orderId}/cancel")
    suspend fun cancel(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String,
    ): Envelope<Order>

    @GET("api/v1/staff/stores/{storeId}/orders")
    suspend fun staffOrders(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Query("status") status: OrderStatus,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Envelope<List<Order>>

    @GET("api/v1/staff/stores/{storeId}/orders/{orderId}")
    suspend fun staffOrder(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Envelope<Order>

    @POST("api/v1/staff/stores/{storeId}/orders/{orderId}/start")
    suspend fun startOrder(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Envelope<Order>

    @POST("api/v1/staff/stores/{storeId}/orders/{orderId}/ready")
    suspend fun markOrderReady(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Envelope<Order>

    @POST("api/v1/staff/stores/{storeId}/orders/{orderId}/confirm-payment")
    suspend fun confirmOrderPayment(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Envelope<PaymentConfirmation>

    @POST("api/v1/staff/stores/{storeId}/orders/redeem")
    suspend fun redeemPickup(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Body request: PickupRequest,
    ): Envelope<PickupRedemption>

    @GET("api/v1/staff/stores/{storeId}/menu-items")
    suspend fun staffMenu(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
    ): Envelope<List<StaffMenuItem>>

    @PATCH("api/v1/staff/stores/{storeId}/menu-items/{productId}")
    suspend fun updateStaffMenu(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @Body patch: StaffMenuPatch,
    ): Envelope<StaffMenuItem>

    @POST("api/v1/staff/stores/{storeId}/stock-adjustments")
    suspend fun adjustStock(
        @Header("Authorization") authorization: String,
        @Path("storeId") storeId: String,
        @Body request: StockAdjustmentRequest,
    ): Envelope<StockAdjustmentResult>

}

class ApiFailure(
    val status: Int,
    val code: String?,
    val requestId: String?,
    message: String,
) : Exception(message)

class Backend(baseUrl: String, private val sessions: SessionStore) {
    internal val gson = Gson()
    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(LorongTeaApi::class.java)

    internal suspend fun <T> request(
        read: Boolean,
        session: Session? = null,
        call: suspend LorongTeaApi.() -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return api.call()
            } catch (error: HttpException) {
                if (error.code() == 401 && session != null) sessions.expire(session)
                val retryAfter = error.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                val wait = when (error.code()) {
                    429 -> retryAfter?.takeIf { it in 0..30 }?.times(1000)
                    502, 503, 504 -> 400L
                    else -> null
                }
                if (!read || attempt++ > 0 || wait == null) throw failure(error)
                delay(wait)
            } catch (_: JsonParseException) {
                throw ApiFailure(502, null, null, "The server returned an unexpected response.")
            } catch (error: IOException) {
                if (!read || attempt++ > 0) throw error
                delay(400)
            }
        }
    }

    private fun failure(error: HttpException): ApiFailure {
        val response = error.response()
        val parsed = try {
            response?.errorBody()?.use { gson.fromJson(it.string(), ErrorBody::class.java) }
        } catch (_: JsonParseException) {
            null
        } catch (_: IOException) {
            null
        }
        return ApiFailure(
            error.code(),
            parsed?.code,
            parsed?.requestId ?: response?.headers()?.get("X-Request-ID"),
            parsed?.message ?: "The server returned HTTP ${error.code()}.",
        )
    }
}
