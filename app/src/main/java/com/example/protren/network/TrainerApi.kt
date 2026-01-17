package com.example.protren.network

import com.example.protren.model.Trainer
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/** Body do wysłania prośby o współpracę (kiedyś) – teraz już nieużywane w nowym flow */
data class CoachingRequestBody(
    val trainerId: String,
    val message: String? = null
)

/**
 * DTO do zapisu oferty trenera w panelu trenera.
 *
 * Pola:
 *  - name, bio, specialties, priceMonth, avatarUrl – podstawowe info o ofercie
 *  - galleryUrls – ewentualne dodatkowe zdjęcia (używane w TrainerOfferViewModel)
 *  - email – opcjonalny e-mail kontaktowy trenera (też używany w ViewModelu)
 *
 * Backend przyjmuje te pola w kontrolerze upsertMyOffer.
 */
data class TrainerUpsertRequest(
    val name: String,
    val bio: String,
    val specialties: List<String>? = null,
    val priceMonth: Double? = null,
    val avatarUrl: String? = null,
    val galleryUrls: List<String>? = null,
    val email: String? = null
)

/** Odpowiedź z zakupu trenera (symulacja backendu) */
data class PurchaseTrainerResponse(
    @SerializedName("trainerId") val trainerId: String,
    @SerializedName("trainerUserId") val trainerUserId: String,
    @SerializedName("subscriptionActive") val subscriptionActive: Boolean,
    @SerializedName("subscriptionUntil") val subscriptionUntil: String,
    @SerializedName("msg") val msg: String? = null
)

/** Odpowiedź z uploadu avatara */
data class FileUploadResponse(
    val url: String
)

/** Odpowiedź z uploadu galerii */
data class GalleryUploadResponse(
    val urls: List<String> = emptyList()
)

/** Body do ustawiania limitu podopiecznych */
data class TrainerSettingsRequest(
    val maxTrainees: Int
)

/** Odpowiedź z endpointu ustawień trenera */
data class TrainerSettingsResponse(
    val ok: Boolean,
    val maxTrainees: Int
)

interface TrainerApi {

    // ───────── PUBLIC ─────────
    @GET("api/trainers")
    suspend fun listTrainers(): Response<List<Trainer>>

    @GET("api/trainers/{id}")
    suspend fun getTrainer(@Path("id") id: String): Response<Trainer>


    // ───────── TRENER: własna oferta ─────────
    @GET("api/trainers/me")
    suspend fun getMyOffer(): Response<Trainer>

    @PUT("api/trainers/me")
    suspend fun upsertMyOffer(@Body body: TrainerUpsertRequest): Response<Trainer>


    // ───────── UŻYTKOWNIK: wykupienie współpracy z trenerem ─────────
    @POST("api/trainers/{id}/purchase")
    suspend fun purchaseTrainer(@Path("id") id: String): Response<PurchaseTrainerResponse>


    // ───────── USTAWIENIA TRENERA (LIMIT PODOPIECZNYCH) ─────────
    @PUT("api/trainers/me/settings")
    suspend fun updateTrainerSettings(
        @Body body: TrainerSettingsRequest
    ): Response<TrainerSettingsResponse>


    // ───────── UPLOAD ZDJĘĆ OFERTY TRENERA ─────────

    @Multipart
    @POST("api/trainers/me/avatar")
    suspend fun uploadTrainerAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<FileUploadResponse>

    @Multipart
    @POST("api/trainers/me/gallery")
    suspend fun uploadTrainerGallery(
        @Part images: List<MultipartBody.Part>
    ): Response<GalleryUploadResponse>
}
