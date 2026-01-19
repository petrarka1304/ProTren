package com.example.protren.network

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val accessToken: String, val refreshToken: String)

interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>
}

interface RefreshApiBlocking {
    @POST("api/auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<RefreshResponse>
}
