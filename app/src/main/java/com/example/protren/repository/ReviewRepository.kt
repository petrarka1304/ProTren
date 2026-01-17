package com.example.protren.repository

import com.example.protren.network.ReviewApi
import com.example.protren.model.AddReviewRequest

class ReviewRepository(
    private val api: ReviewApi
) {

    suspend fun getReviews(trainerId: String) =
        api.getTrainerReviews(trainerId)

    suspend fun addReview(req: AddReviewRequest) =
        api.addReview(req)

    suspend fun updateReview(reviewId: String, req: AddReviewRequest) =
        api.updateReview(reviewId, req)

    suspend fun deleteReview(id: String) =
        api.deleteReview(id)
}
