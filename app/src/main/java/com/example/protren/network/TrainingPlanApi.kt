package com.example.protren.network

import com.example.protren.model.TrainingPlan
import com.example.protren.model.TrainingPlanDay
import retrofit2.Response
import retrofit2.http.*

data class TrainingPlanDto(
    val _id: String,
    val name: String,
    val days: List<TrainingPlanDay> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ExerciseRequest(
    val name: String,
    val muscleGroup: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val rir: Int,
    val pattern: String = ""
)

data class TrainingPlanDayCreateDto(
    val title: String,
    val exercises: List<ExerciseRequest> = emptyList()
)

@Deprecated(
    message = "Użyj TrainingPlanDayCreateDto",
    replaceWith = ReplaceWith("TrainingPlanDayCreateDto")
)
typealias TrainingPlanDayDto = TrainingPlanDayCreateDto

data class TrainingPlanCreateRequest(
    val name: String,
    val days: List<TrainingPlanDayCreateDto>,
    val isPublic: Boolean = false
)

data class TrainingPlanUpdateRequest(
    val name: String? = null,
    val days: List<TrainingPlanDayCreateDto>? = null,
    val isPublic: Boolean? = null
)

data class TrainingPlanResponse(
    val id: String,
    val name: String
)

interface TrainingPlanApi {

    @GET("api/training-plans")
    suspend fun getPlans(): Response<List<TrainingPlanDto>>

    @GET("api/training-plans/{id}")
    suspend fun getPlan(@Path("id") id: String): Response<TrainingPlanDto>

    @POST("api/training-plans")
    suspend fun createPlan(@Body body: TrainingPlanCreateRequest): Response<TrainingPlanResponse>

    @POST("api/training-plans")
    suspend fun createPlanForClient(
        @Query("clientId") clientId: String,
        @Body body: TrainingPlanCreateRequest
    ): Response<TrainingPlanResponse>

    @PUT("api/training-plans/{id}")
    suspend fun updatePlan(
        @Path("id") id: String,
        @Body body: TrainingPlanUpdateRequest
    ): Response<TrainingPlanResponse>

    @DELETE("api/training-plans/{id}")
    suspend fun deletePlan(@Path("id") id: String): Response<Unit>
}

fun TrainingPlanDto.toModel(): TrainingPlan =
    TrainingPlan(
        id = _id,
        name = name,
        days = days,
        isPublic = isPublic,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
