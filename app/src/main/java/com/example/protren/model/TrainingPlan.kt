package com.example.protren.model

data class TrainingPlan(
    val id: String,
    val name: String,
    val days: List<TrainingPlanDay> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class TrainingPlanDay(
    val title: String,
    val exercises: List<Exercise> = emptyList()
)
