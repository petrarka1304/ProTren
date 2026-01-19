package com.example.protren.network

import com.example.protren.model.Supplement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path


interface TrainerPlanApi {

    @GET("api/trainer-user-supplements/{traineeId}")
    suspend fun listSupplements(
        @Path("traineeId") traineeId: String
    ): Response<List<Supplement>>

    @POST("api/trainer-user-supplements/{traineeId}")
    suspend fun createSupplement(
        @Path("traineeId") traineeId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Supplement>

    @PATCH("api/trainer-user-supplements/{traineeId}/{id}")
    suspend fun updateSupplement(
        @Path("traineeId") traineeId: String,
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Supplement>

    @DELETE("api/trainer-user-supplements/{traineeId}/{id}")
    suspend fun deleteSupplement(
        @Path("traineeId") traineeId: String,
        @Path("id") id: String
    ): Response<Unit>

    @POST("api/trainer-user-supplements/{traineeId}/{id}/take-today")
    suspend fun takeToday(
        @Path("traineeId") traineeId: String,
        @Path("id") id: String
    ): Response<Supplement>

    @POST("api/trainer-user-supplements/{traineeId}/{id}/undo-today")
    suspend fun undoToday(
        @Path("traineeId") traineeId: String,
        @Path("id") id: String
    ): Response<Supplement>
}
