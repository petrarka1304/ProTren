package com.example.protren.util

import com.example.protren.model.TrainingPlan

fun wywnioskujTagPlanu(plan: TrainingPlan): String {
    val titles = plan.days.joinToString(" ") { it.title }.lowercase()
    return when {
        "full" in titles -> "Full Body" // Zmieniłem na angielski standard z Twojego PlanDetails
        listOf("upper", "góra").any { it in titles } &&
                listOf("lower", "dół", "nogi").any { it in titles } -> "Upper / Lower"
        listOf("push", "pull", "legs", "nogi").count { it in titles } >= 2 -> "PPL"
        else -> "Własny" // lub "Plan niestandardowy"
    }
}