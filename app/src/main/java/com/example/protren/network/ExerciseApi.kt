package com.example.protren.network

import retrofit2.Response
import retrofit2.http.*

/** Pojedyncze ćwiczenie z backendu. */
data class ExerciseDto(
    val _id: String,
    val name: String,
    val group: String? = null,
    val equipment: String? = null,
    val tags: List<String> = emptyList()
)

/** Strona danych z backendu. */
data class ExercisePageDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val items: List<ExerciseDto>
)

/** Zgrupowane nazwy grup mięśniowych. */
data class GroupDto(
    val name: String,
    val count: Int
)

/** Dodawanie własnego ćwiczenia. */
data class CreateExerciseRequest(
    val name: String,
    val group: String? = null,
    val equipment: String? = null,
    val tags: List<String> = emptyList()
)

interface ExerciseApi {

    /** Lista ćwiczeń */
    @GET("api/exercises")
    suspend fun getExercises(
        @Query("query") query: String? = null,
        @Query("group") group: String? = null,
        @Query("equipment") equipment: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("mine") mine: Boolean? = false
    ): Response<ExercisePageDto>

    /** Dostępne grupy mięśniowe. */
    @GET("api/exercises/groups")
    suspend fun getGroups(): Response<List<GroupDto>>

    /** Dodanie własnego ćwiczenia. */
    @POST("api/exercises")
    suspend fun createExercise(
        @Body body: CreateExerciseRequest
    ): Response<ExerciseDto>

    /** Usunięcie własnego ćwiczenia. */
    @DELETE("api/exercises/{id}")
    suspend fun deleteExercise(
        @Path("id") id: String
    ): Response<Unit>
}
