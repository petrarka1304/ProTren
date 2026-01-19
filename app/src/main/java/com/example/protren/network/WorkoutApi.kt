package com.example.protren.network

import com.example.protren.model.WorkoutLog
import com.example.protren.model.Exercise
import com.example.protren.model.CreateWorkoutRequest
import retrofit2.Response
import retrofit2.http.*

data class WeeklyDay(
    val date: String,
    val sets: Int,
    val reps: Int,
    val volume: Int
)

data class WeeklySummaryResponse(
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalVolume: Int = 0,
    val days: List<WeeklyDay> = emptyList()
)

data class UserData(val email: String = "")

data class UpdateWorkoutRequest(
    val date: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val trainingPlanId: String? = null
)

interface WorkoutApi {

    @GET("api/users/me")
    suspend fun getUser(): Response<UserData>

    @GET("api/workouts/summary-week")
    suspend fun getWeeklySummary(): Response<WeeklySummaryResponse>

    @GET("api/workouts/summary-range")
    suspend fun getSummaryByDays(
        @Query("days") days: Int
    ): Response<WeeklySummaryResponse>

    @POST("api/workouts")
    suspend fun createWorkout(@Body body: CreateWorkoutRequest): Response<WorkoutLog>

    @GET("api/workouts")
    suspend fun getWorkoutLogs(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<List<WorkoutLog>>

    @GET("api/workouts/{id}")
    suspend fun getWorkout(@Path("id") id: String): Response<WorkoutLog>

    @PUT("api/workouts/{id}")
    suspend fun updateWorkout(
        @Path("id") id: String,
        @Body body: UpdateWorkoutRequest
    ): Response<WorkoutLog>

    @DELETE("api/workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: String): Response<Unit>

    @GET("api/workouts/today")
    suspend fun getTodayWorkout(): Response<WorkoutLog>

}
