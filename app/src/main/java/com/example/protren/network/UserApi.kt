package com.example.protren.network

import com.example.protren.model.MeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserApi {

    @GET("api/users/me")
    suspend fun getMe(): Response<MeResponse>

    @PUT("api/users/me")
    suspend fun updateMe(
        @Body body: Map<String, String>
    ): Response<MeResponse>

    @DELETE("api/users/me")
    suspend fun deleteMe(): Response<Unit>
}
