package com.example.protren.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Odpowiedź z /api/users/me
 * Zgodna z backendem: { id, email, role, createdAt?, updatedAt? }
 */
data class MeResponse(
    val id: String,
    val email: String,
    val role: String = "user",
    val name: String? = null,           // jeśli kiedyś backend doda, nie wywali deserializacji
    val createdAt: String? = null,
    val updatedAt: String? = null
)

interface RoleApi {
    // Uwaga: ApiClient ma baseUrl zakończony na /api/, więc tu używamy ścieżki względnej bez /api.
    @GET("api/users/me")
    suspend fun me(): Response<MeResponse>
}

/* ─────────────────────────────────────────────────────────────
   Helper: bezpieczne pobranie roli (mapuje HTTP na Result<>)
   ───────────────────────────────────────────────────────────── */
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
