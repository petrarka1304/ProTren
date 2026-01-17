package com.example.protren.repository

import com.example.protren.model.Supplement
import com.example.protren.network.SupplementApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class SupplementRepository(
    private val api: SupplementApi
) {
    suspend fun getAll(): Response<List<Supplement>> = safe { api.getAll() }
    suspend fun getToday(): Response<List<Supplement>> = safe { api.getToday() }
    suspend fun create(s: Supplement): Response<Supplement> = safe { api.create(s) }
    suspend fun update(id: String, s: Supplement): Response<Supplement> = safe { api.update(id, s) }
    suspend fun delete(id: String): Response<Unit> = safe { api.delete(id) }
    suspend fun take(id: String): Response<Unit> = safe { api.take(id) }
    suspend fun undoTake(id: String): Response<Unit> = safe { api.undoTake(id) }

    private inline fun <T> safe(block: () -> Response<T>): Response<T> = try {
        block()
    } catch (e: Exception) {
        val body = (e.message ?: "Network error").toResponseBody("text/plain".toMediaTypeOrNull())
        @Suppress("UNCHECKED_CAST")
        Response.error<T>(599, body)
    }
}
