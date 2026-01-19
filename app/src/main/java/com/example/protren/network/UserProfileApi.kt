package com.example.protren.network

import com.example.protren.model.UserProfile
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*



data class SaveProfileResponse(
    val msg: String? = null,
    val profile: UserProfile? = null
)

data class NutritionDetails(
    val bmr: Int? = null,
    val tdee: Int? = null,
    val activity: String? = null
)

data class NutritionResponse(
    val msg: String? = null,
    val profile: UserProfile? = null,
    val details: NutritionDetails? = null
)

data class AvatarUploadResponse(
    @SerializedName("avatarKey")
    val avatarKey: String? = null,

    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,

    @SerializedName("profile")
    val profile: UserProfile? = null
)

data class TraineeProfileResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("userId")
    val userId: String? = null,

    @SerializedName("email")
    val email: String,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("firstName")
    val firstName: String? = null,

    @SerializedName("lastName")
    val lastName: String? = null,

    @SerializedName("role")
    val role: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null,

    @SerializedName("profile")
    val profile: UserProfile? = null
)

interface UserProfileApi {

    @GET("api/profile")
    suspend fun getProfile(): Response<UserProfile>

    @PUT("api/profile")
    suspend fun saveMyProfile(
        @Body profile: UserProfile
    ): Response<SaveProfileResponse>

    @PUT("api/profile/{userId}")
    suspend fun saveUserProfile(
        @Path("userId") userId: String,
        @Body profile: UserProfile
    ): Response<SaveProfileResponse>

    @POST("api/profile/nutrition")
    suspend fun calculateNutrition(
        @Body profile: UserProfile
    ): Response<NutritionResponse>

    @Multipart
    @POST("api/profile-uploads/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("meta") metaJson: RequestBody? = null
    ): Response<AvatarUploadResponse>


    @GET("api/files/view")
    suspend fun viewFile(
        @Query("key") key: String
    ): Response<FileViewResponse>

    @GET("api/trainer/trainee/{userId}/profile")
    suspend fun getUserProfile(
        @Path("userId") userId: String
    ): Response<TraineeProfileResponse>
}
