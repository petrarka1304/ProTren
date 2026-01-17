package com.example.protren.model

data class SupplementCatalogItem(
    val _id: String? = null,
    val name: String,
    val defaultDosage: String? = null,
    val defaultTimes: List<String>? = null,
    val defaultDaysOfWeek: List<Int>? = null,
    val category: String? = null,
    val description: String? = null,
    val isActive: Boolean? = null
)
