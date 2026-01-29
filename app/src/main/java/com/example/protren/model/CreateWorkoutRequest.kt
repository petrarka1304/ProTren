package com.example.protren.model

data class CreateWorkoutRequest(
    val date: String,
    val status: String,
    val title: String? = null,
    val exercises: List<Exercise>,
    val trainingPlanId: String? = null
)
