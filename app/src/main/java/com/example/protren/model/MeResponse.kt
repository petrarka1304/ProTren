package com.example.protren.model

data class MeResponse(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val role: String = "user"
)
