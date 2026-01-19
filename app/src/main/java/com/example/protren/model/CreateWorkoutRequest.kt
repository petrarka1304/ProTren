package com.example.protren.model

data class CreateWorkoutRequest(
    val trainingPlanId: String? = null,
    val status: String? = null,
    val date: String? = null, // np. "2025-10-09" albo null
    val exercises: List<Exercise>
)
