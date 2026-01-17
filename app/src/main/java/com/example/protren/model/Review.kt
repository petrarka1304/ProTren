package com.example.protren.model

data class Review(
    val id: String,
    val trainerId: String,
    val userId: String,
    val userEmail: String?,
    val rating: Int,
    val comment: String,
    val createdAt: String,
    val updatedAt: String
)