package com.example.protren.network

import com.example.protren.model.Supplement
import com.example.protren.model.SupplementCatalogItem
import retrofit2.Response
import retrofit2.http.*

interface SupplementApi {

    @GET("api/supplements")
    suspend fun getAll(): Response<List<Supplement>>

    @POST("api/supplements")
    suspend fun create(@Body body: Supplement): Response<Supplement>

    @PUT("api/supplements/{id}")
    suspend fun update(@Path("id") id: String, @Body body: Supplement): Response<Supplement>

    @DELETE("api/supplements/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @GET("api/supplements/today")
    suspend fun getToday(): Response<List<Supplement>>

    @POST("api/supplements/take/{id}")
    suspend fun take(@Path("id") id: String): Response<Unit>

    @POST("api/supplements/undo/{id}")
    suspend fun undoTake(@Path("id") id: String): Response<Unit>

    @GET("api/supplements/catalog")
    suspend fun getCatalog(): Response<List<SupplementCatalogItem>>
}