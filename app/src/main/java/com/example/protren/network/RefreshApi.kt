package com.example.protren.network

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// ── modele ─────────────────────────────────────────────────────────────────────
data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val accessToken: String, val refreshToken: String)

// ── wariant korutynowy (gdy wywołujesz z ViewModelu) ──────────────────────────
interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>
}

// ── wariant BLOKUJĄCY do użycia w OkHttp Interceptor (bez korutyn) ────────────
interface RefreshApiBlocking {
    @POST("api/auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<RefreshResponse>
}
