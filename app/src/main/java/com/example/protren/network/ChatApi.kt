package com.example.protren.network

import retrofit2.Response
import retrofit2.http.*

//DTO: listy czatów
data class ChatSummaryDto(
    val id: String,
    val otherUserId: String?,
    val otherName: String?,
    val otherAvatarUrl: String?,
    val lastMessageText: String?,
    val lastMessageAt: String?,
    val unreadCount: Int?,
    val relation: String?,

    val otherOnline: Boolean? = null,
    val otherLastActiveAt: String? = null
)

data class StartChatRequest(
    val userId: String
)

//DTO: załączniki
data class ChatAttachmentDto(
    val type: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val name: String? = null
)

data class ReplyRef(
    val id: String,
    val senderId: String,
    val senderName: String?,
    val text: String? = null
)

//DTO: wiadomości
data class ChatMessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String? = null,
    val text: String?,
    val createdAt: String,
    val attachments: List<ChatAttachmentDto>? = null,
    val status: String? = null,
    val reactions: Map<String, Int>? = null,
    val replyTo: ReplyRef? = null
)

//DTO: requesty
data class SendMessageRequest(
    val text: String,
    val replyToId: String? = null
)

data class TypingRequest(
    val value: Boolean
)


data class MarkReadRequest(
    val lastReadAt: String? = null,
    val lastReadMessageId: String? = null
)

//API
interface ChatApi {


    @GET("api/chats/my")
    suspend fun list(): Response<List<ChatSummaryDto>>

    @POST("api/chats/start")
    suspend fun startOrGet(@Body body: StartChatRequest): Response<ChatSummaryDto>

    @GET("api/chats/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = 50
    ): Response<List<ChatMessageDto>>

    @POST("api/chats/{chatId}/messages")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body body: SendMessageRequest
    ): Response<ChatMessageDto>

    @POST("api/chats/{chatId}/read")
    suspend fun markRead(
        @Path("chatId") chatId: String,
        @Body body: MarkReadRequest? = null
    ): Response<Unit>

    @POST("api/chats/{chatId}/typing")
    suspend fun setTyping(
        @Path("chatId") chatId: String,
        @Body body: TypingRequest
    ): Response<Unit>

    @DELETE("api/chats/{chatId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String
    ): Response<Unit>

    @GET("api/files/view")
    suspend fun viewFile(
        @Query("key") key: String
    ): Response<FileViewResponse>
}

fun String.looksLikeHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
