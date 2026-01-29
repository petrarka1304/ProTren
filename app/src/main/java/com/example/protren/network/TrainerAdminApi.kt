package com.example.protren.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class CoachingRequestItem(
    val id: String,
    val traineeId: String,
    val traineeName: String,
    val message: String,
    val createdAt: String,
)

data class RespondBody(val requestId: String, val accept: Boolean)

data class TraineeItem(
    val userId: String,
    val name: String,
    val email: String,
    val subscriptionActive: Boolean? = null,
    val subscriptionUntil: String? = null,
    val avatarUrl: String? = null
)

data class TrainerCreatePlanRequest(
    val name: String,
    val days: List<TrainingPlanDayCreateDto>,
    val isPublic: Boolean
)


interface TrainerAdminApi {
    @GET("api/coaching/requests")
    suspend fun listRequests(): Response<List<CoachingRequestItem>>

    @POST("api/coaching/respond")
    suspend fun respond(@Body body: RespondBody): Response<Unit>

    @GET("api/trainer/trainees")
    suspend fun listTrainees(): Response<List<TraineeItem>>

    // Tworzenie planu przez trenera dla usera
    @POST("api/training-plans")
    suspend fun createPlanForUser(
        @Query("clientId") clientId: String,
        @Body body: TrainerCreatePlanRequest
    ): Response<TrainingPlanResponse>
}