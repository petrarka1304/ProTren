package com.example.protren.network

import retrofit2.Response
import retrofit2.http.*

data class ExerciseDto(
    val _id: String,
    val name: String,
    val group: String? = null,
    val equipment: String? = null,
    val tags: List<String> = emptyList()
)

data class ExercisePageDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val items: List<ExerciseDto>
)

data class GroupDto(
    val name: String,
    val count: Int
)

data class CreateExerciseRequest(
    val name: String,
    val group: String? = null,
    val equipment: String? = null,
    val tags: List<String> = emptyList()
)

interface ExerciseApi {

    @GET("api/exercises")
    suspend fun getExercises(
        @Query("query") query: String? = null,
        @Query("group") group: String? = null,
        @Query("equipment") equipment: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("mine") mine: Boolean? = false
    ): Response<ExercisePageDto>

    @GET("api/exercises/groups")
    suspend fun getGroups(): Response<List<GroupDto>>

    @POST("api/exercises")
    suspend fun createExercise(
        @Body body: CreateExerciseRequest
    ): Response<ExerciseDto>

    @DELETE("api/exercises/{id}")
    suspend fun deleteExercise(
        @Path("id") id: String
    ): Response<Unit>
}
