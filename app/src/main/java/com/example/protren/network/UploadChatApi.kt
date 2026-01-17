package com.example.protren.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface UploadChatApi {

    // ---------- IMAGES (SINGLE) ----------

    @Multipart
    @POST("api/chats/{chatId}/upload/images")
    suspend fun uploadImage(
        @Path("chatId") chatId: String,
        @Part file: MultipartBody.Part
    ): Response<ChatUploadMessageResponse>

    @Multipart
    @POST("api/chats/{chatId}/upload/images")
    suspend fun uploadImageWithMeta(
        @Path("chatId") chatId: String,
        @Part file: MultipartBody.Part,
        @Part("meta") meta: RequestBody
    ): Response<ChatUploadMessageResponse>

    // ---------- VIDEOS (SINGLE) ----------

    @Multipart
    @POST("api/chats/{chatId}/upload/videos")
    suspend fun uploadVideo(
        @Path("chatId") chatId: String,
        @Part file: MultipartBody.Part
    ): Response<ChatUploadMessageResponse>

    @Multipart
    @POST("api/chats/{chatId}/upload/videos")
    suspend fun uploadVideoWithMeta(
        @Path("chatId") chatId: String,
        @Part file: MultipartBody.Part,
        @Part("meta") meta: RequestBody
    ): Response<ChatUploadMessageResponse>
}

// jeśli masz już tę klasę w projekcie, NIE duplikuj – użyj swojej
data class ChatUploadMessageResponse(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val createdAt: String,
    val attachments: List<ChatAttachmentDto>? = null
)
