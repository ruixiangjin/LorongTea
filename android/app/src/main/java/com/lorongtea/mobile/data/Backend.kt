package com.lorongtea.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class Store(val id: String, val code: String, val name: String)

internal data class Envelope<T>(val data: T)
internal data class ErrorBody(val code: String?, val message: String?, val requestId: String?)

internal interface LorongTeaApi {
    @GET("api/v1/stores")
    suspend fun stores(): Envelope<List<Store>>
}

class ApiFailure(
    val status: Int,
    val code: String?,
    val requestId: String?,
    message: String,
) : Exception(message)

class Backend(baseUrl: String) {
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

    suspend fun stores(): List<Store> = request(read = true) { api.stores().data }

    private suspend fun <T> request(read: Boolean, call: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return call()
            } catch (error: HttpException) {
                val retryAfter = error.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                val wait = when (error.code()) {
                    429 -> retryAfter?.takeIf { it in 0..30 }?.times(1000)
                    502, 503, 504 -> 400L
                    else -> null
                }
                if (!read || attempt++ > 0 || wait == null) throw failure(error)
                delay(wait)
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
