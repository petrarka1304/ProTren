package com.example.protren.model

data class LoginResponse(
    val token: String,
    val user: UserData
)

data class UserData(
    val id: String,
    val email: String,
    val role: String,
    val name: String? = null
)
