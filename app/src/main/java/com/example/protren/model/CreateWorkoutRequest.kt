package com.example.protren.model

/**
 * DTO do tworzenia logu treningowego (POST /api/workouts).
 * Nie zawiera _id – backend nadaje je sam.
 * 'date' może być null – backend ustawi domyślnie "teraz".
 */
data class CreateWorkoutRequest(
    val trainingPlanId: String? = null,
    val status: String? = null,
    val date: String? = null, // np. "2025-10-09" albo null
    val exercises: List<Exercise>
)
