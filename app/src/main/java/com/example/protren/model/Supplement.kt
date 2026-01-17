package com.example.protren.model

data class Supplement(
    val _id: String? = null,
    val name: String? = null,
    val dosage: String? = null,
    val notes: String? = null,
    val times: List<String>? = emptyList(),        // "morning", "midday", "evening", "night"
    val daysOfWeek: List<Int>? = emptyList(),      // 0..6 (Nd..Sb)
    val takenLog: List<TakenLog>? = emptyList(),
    // tylko w GET /supplements/today:
    val takenToday: Boolean? = null
)

data class TakenLog(
    val date: String,
    val at: String? = null
)
