package com.lorongtea.mobile.data

class CatalogRepository(private val backend: Backend) {
    suspend fun stores(): List<Store> = backend.request(read = true) { stores().data }
}
