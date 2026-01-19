package com.example.protren.model

import com.google.gson.annotations.SerializedName

data class WorkoutLog(
    @SerializedName("_id")
    val id: String? = null,
    val title: String? = null,
    val trainingPlanId: String? = null,
    val date: String? = null,

    val status: String? = null,

    val exercises: List<Exercise>? = emptyList()
)

data class Exercise(
    val name: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weight: Int? = null,
    val notes: String? = null
)
