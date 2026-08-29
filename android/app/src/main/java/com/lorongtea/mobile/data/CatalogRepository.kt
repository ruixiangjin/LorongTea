package com.lorongtea.mobile.data

data class MenuItem(
    val productId: String,
    val name: String,
    val priceCents: Long,
    val listed: Boolean,
)

class CatalogRepository(private val backend: Backend) {
    suspend fun stores(): List<Store> = backend.request(read = true) { stores().data }

    suspend fun menu(storeId: String): List<MenuItem> = backend.request(read = true) { menu(storeId).data }
}
