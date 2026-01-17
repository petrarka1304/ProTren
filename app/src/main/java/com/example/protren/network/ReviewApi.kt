package com.example.protren.network

import com.example.protren.model.AddReviewRequest
import com.example.protren.model.Review
import retrofit2.Response
import retrofit2.http.*

interface ReviewApi {

    @GET("api/reviews/trainer/{trainerId}")
    suspend fun getTrainerReviews(
        @Path("trainerId") id: String
    ): Response<List<Review>>

    @POST("api/reviews")
    suspend fun addReview(
        @Body body: AddReviewRequest
    ): Response<Review>

    @PUT("api/reviews/{reviewId}")
    suspend fun updateReview(
        @Path("reviewId") reviewId: String,
        @Body body: AddReviewRequest
    ): Response<Review>

    @DELETE("api/reviews/{reviewId}")
    suspend fun deleteReview(
        @Path("reviewId") reviewId: String
    ): Response<Unit>
}
