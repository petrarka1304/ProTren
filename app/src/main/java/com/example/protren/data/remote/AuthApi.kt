// app/src/main/java/com/example/protren/data/remote/AuthApi.kt
package com.example.protren.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val role: String = "user")

data class LoginResponse(
    val token: String?,           // = accessToken
    val accessToken: String,
    val refreshToken: String,
    val user: LoginUser
)

data class LoginUser(
    val id: String,
    val email: String,
    val role: String
)

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(
    val token: String,
    val password: String
)

data class ApiMessage(
    val msg: String?
)

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Unit>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ApiMessage>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<ApiMessage>
}
