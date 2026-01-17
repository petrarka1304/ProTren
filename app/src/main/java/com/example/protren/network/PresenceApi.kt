package com.example.protren.network

data class PresenceResponse(
    val online: Boolean,
    val lastActiveAt: String?
)

interface PresenceApi {
    @retrofit2.http.GET("api/users/{id}/presence")
    suspend fun getPresence(
        @retrofit2.http.Path("id") userId: String
    ): retrofit2.Response<PresenceResponse>
}
