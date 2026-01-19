package com.example.protren.model

import androidx.annotation.Keep

@Keep
data class SupplementPlanItem(
    val catalogItemId: String? = null,
    val supplementName: String,
    val dose: String,
    val timing: String = "",
    val frequency: String = "daily",
    val daysOfWeek: List<Int> = emptyList(),
    val notes: String = ""
)

@Keep
data class SupplementPlanDto(
    val _id: String,
    val user: String,
    val createdBy: String,
    val name: String,
    val supplements: List<SupplementPlanItem> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val isActive: Boolean = true,
    val notes: String = ""
)
