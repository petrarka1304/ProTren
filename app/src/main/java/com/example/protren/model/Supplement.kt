package com.example.protren.model

data class Supplement(
    val _id: String? = null,
    val name: String? = null,
    val dosage: String? = null,
    val notes: String? = null,
    val times: List<String>? = emptyList(),
    val daysOfWeek: List<Int>? = emptyList(),
    val takenLog: List<TakenLog>? = emptyList(),
    val takenToday: Boolean? = null
)

data class TakenLog(
    val date: String,
    val at: String? = null
)
