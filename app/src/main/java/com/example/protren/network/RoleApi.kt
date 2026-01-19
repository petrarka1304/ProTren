package com.example.protren.network

import retrofit2.Response
import retrofit2.http.GET


data class MeResponse(
    val id: String,
    val email: String,
    val role: String = "user",
    val name: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

interface RoleApi {
    @GET("api/users/me")
    suspend fun me(): Response<MeResponse>
}

suspend fun RoleApi.meSafe(): Result<MeResponse> {
    return try {
        val r = me()
        if (r.isSuccessful) {
            val body = r.body()
            if (body != null) Result.success(body)
            else Result.failure(IllegalStateException("Pusta odpowiedź /users/me"))
        } else {
            Result.failure(IllegalStateException("HTTP ${r.code()} /users/me"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
