package com.example.protren.network

import com.example.protren.model.Supplement
import com.example.protren.model.SupplementCatalogItem
import retrofit2.Response
import retrofit2.http.*

interface SupplementApi {

    // LISTA (GET /api/supplements)
    @GET("api/supplements")
    suspend fun getAll(): Response<List<Supplement>>

    // CREATE (POST /api/supplements)
    @POST("api/supplements")
    suspend fun create(@Body body: Supplement): Response<Supplement>

    // UPDATE (PUT /api/supplements/:id)
    @PUT("api/supplements/{id}")
    suspend fun update(@Path("id") id: String, @Body body: Supplement): Response<Supplement>

    // DELETE (DELETE /api/supplements/:id)
    @DELETE("api/supplements/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    // TODAY (GET /api/supplements/today)
    @GET("api/supplements/today")
    suspend fun getToday(): Response<List<Supplement>>

    // TAKE (POST /api/supplements/:id/take)
    @POST("api/supplements/{id}/take")
    suspend fun take(@Path("id") id: String): Response<Unit>

    // UNDO TAKE (DELETE /api/supplements/:id/take)
    @DELETE("api/supplements/{id}/take")
    suspend fun undoTake(@Path("id") id: String): Response<Unit>

    @GET("api/supplements/catalog")
    suspend fun getCatalog(): Response<List<SupplementCatalogItem>>
}
