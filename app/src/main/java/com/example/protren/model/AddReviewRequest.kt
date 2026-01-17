package com.example.protren.model

data class AddReviewRequest(
    val trainerId: String,
    val rating: Int,
    val comment: String? = null
)