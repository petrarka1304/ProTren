package com.example.protren.network

import com.example.protren.model.UserProfile
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// -------------------- DTO --------------------

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

/**
 * Cloudflare R2:
 * Backend po uploadzie avatara zwykle zwraca avatarKey (klucz w R2) + czasem profile.
 * avatarUrl może się pojawić tylko jeśli backend dodatkowo wygeneruje signed URL.
 *
 * Dlatego pola są nullable -> nie wywalasz się na parse/NullPointer.
 */
data class AvatarUploadResponse(
    @SerializedName("avatarKey")
    val avatarKey: String? = null,

    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,

    @SerializedName("profile")
    val profile: UserProfile? = null
)



// 🔹 ODPOWIEDŹ DLA TRENERA – PROFIL PODOPIECZNEGO
// 🔹 zgodna z backendem po poprawkach
data class TraineeProfileResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("userId")
    val userId: String? = null,

    @SerializedName("email")
    val email: String,

    // ✅ TO MASZ NA LIŚCIE /api/trainer/trainees
    @SerializedName("name")
    val name: String? = null,

    // ✅ JEŚLI backend też wyśle — super, ale nie wymagamy
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

// -------------------- API --------------------

interface UserProfileApi {

    // GET /api/profile
    @GET("api/profile")
    suspend fun getProfile(): Response<UserProfile>

    // PUT /api/profile  (upsert własnego profilu)
    @PUT("api/profile")
    suspend fun saveMyProfile(
        @Body profile: UserProfile
    ): Response<SaveProfileResponse>

    // PUT /api/profile/{userId}  (trener/admin)
    @PUT("api/profile/{userId}")
    suspend fun saveUserProfile(
        @Path("userId") userId: String,
        @Body profile: UserProfile
    ): Response<SaveProfileResponse>

    // POST /api/profile/nutrition
    @POST("api/profile/nutrition")
    suspend fun calculateNutrition(
        @Body profile: UserProfile
    ): Response<NutritionResponse>

    // POST /api/profile-uploads/me/avatar
    @Multipart
    @POST("api/profile-uploads/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("meta") metaJson: RequestBody? = null
    ): Response<AvatarUploadResponse>

    /**
     * (Opcjonalne) jeśli w UI chcesz od razu rozwiązać avatarKey -> signed URL
     * bez pobierania całego profilu:
     *
     * GET /api/files/view?key=...
     */
    @GET("api/files/view")
    suspend fun viewFile(
        @Query("key") key: String
    ): Response<FileViewResponse>

    // GET /api/trainer/trainee/{userId}/profile
    @GET("api/trainer/trainee/{userId}/profile")
    suspend fun getUserProfile(
        @Path("userId") userId: String
    ): Response<TraineeProfileResponse>
}
