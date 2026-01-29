package com.example.protren.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.*
import com.example.protren.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class ChatThreadViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)
    private val retrofit by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        )
    }

    private val chatApi by lazy { retrofit.create(ChatApi::class.java) }
    private val uploadApi by lazy { retrofit.create(UploadChatApi::class.java) }

    private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val messages: StateFlow<List<ChatMessageDto>> = _messages

    private val _myUserId = MutableStateFlow<String?>(prefs.getUserId())
    val myUserId: StateFlow<String?> = _myUserId

    private val _otherUserId = MutableStateFlow<String?>(null)
    val otherUserId: StateFlow<String?> = _otherUserId

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _otherTyping = MutableStateFlow(false)
    val otherTyping: StateFlow<Boolean> = _otherTyping

    private val _otherName = MutableStateFlow<String?>(null)
    val otherName: StateFlow<String?> = _otherName

    private val _otherAvatarUrl = MutableStateFlow<String?>(null)
    val otherAvatarUrl: StateFlow<String?> = _otherAvatarUrl

    private val _otherOnline = MutableStateFlow(false)
    val otherOnline: StateFlow<Boolean> = _otherOnline

    private val _otherLastActiveAt = MutableStateFlow<String?>(null)
    val otherLastActiveAt: StateFlow<String?> = _otherLastActiveAt

    private var chatId: String? = null
    private var endReached = false
    private var isLoadingMore = false
    private val signedUrlCache = ConcurrentHashMap<String, String>()

    private var lastTypingSent: Boolean? = null
    private var stopTypingJob: Job? = null

    fun load(id: String) {
        chatId = id
        _loading.value = true
        _error.value = null
        endReached = false

        _myUserId.value = prefs.getUserId()

        lastTypingSent = null
        stopTypingJob?.cancel()
        stopTypingJob = null

        viewModelScope.launch {
            try {
                val resp = chatApi.getMessages(id, before = null, limit = 50)
                if (resp.isSuccessful) {
                    val list = resp.body().orEmpty()
                    val sorted = list.sortedByDescending { it.createdAt }

                    val resolved = resolveAttachmentsUrls(sorted)
                    _messages.value = resolved

                    runCatching { chatApi.markRead(id) }

                    runCatching {
                        val metaResp = chatApi.list()
                        if (metaResp.isSuccessful) {
                            val chats = metaResp.body().orEmpty()
                            val current = chats.firstOrNull { it.id == id }
                            _otherName.value = current?.otherName
                            _otherOnline.value = current?.otherOnline == true
                            _otherLastActiveAt.value = current?.otherLastActiveAt
                            _otherUserId.value = current?.otherUserId

                            val rawAvatar = current?.otherAvatarUrl
                            _otherAvatarUrl.value = resolveMaybeKeyToUrl(rawAvatar)
                        }
                    }
                } else {
                    _error.value = "HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore() {
        val id = chatId ?: return
        if (isLoadingMore || endReached) return

        val oldest = _messages.value.minByOrNull { it.createdAt } ?: return
        isLoadingMore = true

        viewModelScope.launch {
            try {
                val resp = chatApi.getMessages(id, before = oldest.createdAt, limit = 50)
                if (resp.isSuccessful) {
                    val more = resp.body().orEmpty()
                    if (more.isEmpty()) {
                        endReached = true
                    } else {
                        val moreSorted = more.sortedByDescending { it.createdAt }
                        val moreResolved = resolveAttachmentsUrls(moreSorted)
                        _messages.value = _messages.value + moreResolved
                    }
                } else {
                    _error.value = "HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun send(text: String, replyToId: String? = null, onDone: (Boolean) -> Unit) {
        val id = chatId ?: return onDone(false)
        viewModelScope.launch {
            try {
                val resp = chatApi.sendMessage(
                    id,
                    SendMessageRequest(text = text, replyToId = replyToId)
                )
                if (resp.isSuccessful) {
                    val msg = resp.body()!!
                    val resolved = resolveAttachmentsUrls(listOf(msg)).first()
                    _messages.value = listOf(resolved) + _messages.value
                    runCatching { chatApi.markRead(id) }
                    onDone(true)
                } else onDone(false)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun delete(messageId: String, onDone: (Boolean) -> Unit = {}) {
        val id = chatId ?: return onDone(false)
        viewModelScope.launch {
            try {
                val resp = chatApi.deleteMessage(id, messageId)
                if (resp.isSuccessful) {
                    _messages.value = _messages.value.filterNot { it.id == messageId }
                    onDone(true)
                } else {
                    onDone(false)
                }
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun sendImages(
        uris: List<Uri>,
        replyToId: String? = null,
        caption: String? = null,
        onDone: (Boolean) -> Unit
    ) {
        val id = chatId ?: return onDone(false)
        val app = getApplication<Application>()

        viewModelScope.launch {
            try {
                val imageParts = ImageUtils.makeGalleryParts(app, uris)
                if (imageParts.isEmpty()) return@launch onDone(false)

                val one = imageParts.first()

                val meta = makeMetaBody(replyToId)
                val resp = uploadApi.uploadImageWithMeta(id, one, meta)

                val msgFromServer = if (resp.isSuccessful) {
                    resp.body()
                } else {
                    val fallback = uploadApi.uploadImage(id, one)
                    if (!fallback.isSuccessful) return@launch onDone(false)
                    fallback.body()
                } ?: return@launch onDone(false)

                val dto = ChatMessageDto(
                    id = msgFromServer.id,
                    chatId = msgFromServer.chatId,
                    senderId = msgFromServer.senderId,
                    text = msgFromServer.text,
                    createdAt = msgFromServer.createdAt,
                    attachments = msgFromServer.attachments
                )

                val resolved = resolveAttachmentsUrls(listOf(dto)).first()
                _messages.value = listOf(resolved) + _messages.value

                if (!caption.isNullOrBlank()) {
                    send(caption, replyToId = replyToId) {}
                }

                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun sendVideo(uri: Uri, replyToId: String? = null, onDone: (Boolean) -> Unit) {
        val id = chatId ?: return onDone(false)
        val app = getApplication<Application>()

        viewModelScope.launch {
            try {
                val cr = app.contentResolver
                val mime = cr.getType(uri) ?: "video/mp4"

                val bytes = cr.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    val out = ByteArrayOutputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                } ?: return@launch onDone(false)

                val requestBody: RequestBody = bytes.toRequestBody(mime.toMediaType())
                val fileName = "video_${System.currentTimeMillis()}.${guessVideoExtension(mime)}"
                val part = MultipartBody.Part.createFormData(
                    name = "files",
                    filename = fileName,
                    body = requestBody
                )

                val meta = makeMetaBody(replyToId)
                val resp = uploadApi.uploadVideoWithMeta(id, part, meta)

                val msgFromServer = if (resp.isSuccessful) {
                    resp.body()
                } else {
                    val fallback = uploadApi.uploadVideo(id, part)
                    if (!fallback.isSuccessful) return@launch onDone(false)
                    fallback.body()
                } ?: return@launch onDone(false)

                val dto = ChatMessageDto(
                    id = msgFromServer.id,
                    chatId = msgFromServer.chatId,
                    senderId = msgFromServer.senderId,
                    text = msgFromServer.text,
                    createdAt = msgFromServer.createdAt,
                    attachments = msgFromServer.attachments
                )

                val resolved = resolveAttachmentsUrls(listOf(dto)).first()
                _messages.value = listOf(resolved) + _messages.value
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun setTyping(isTyping: Boolean) {
        val id = chatId ?: return

        if (!isTyping) {
            stopTypingJob?.cancel()
            stopTypingJob = null

            if (lastTypingSent != false) {
                lastTypingSent = false
                viewModelScope.launch { safeSendTyping(id, false) }
            }
            return
        }

        if (lastTypingSent != true) {
            lastTypingSent = true
            viewModelScope.launch { safeSendTyping(id, true) }
        }

        stopTypingJob?.cancel()
        stopTypingJob = viewModelScope.launch {
            delay(1500)
            if (lastTypingSent == true) {
                lastTypingSent = false
                safeSendTyping(id, false)
            }
        }
    }

    private suspend fun safeSendTyping(id: String, value: Boolean) {
        try {
            chatApi.setTyping(id, TypingRequest(value = value))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (e.message?.contains("Canceled", ignoreCase = true) == true) return
        } catch (_: Exception) {
        }
    }

    fun markRead() {
        val id = chatId ?: return
        viewModelScope.launch { runCatching { chatApi.markRead(id) } }
    }

    fun setOther(name: String?, avatar: String?) {
        _otherName.value = name
        viewModelScope.launch {
            _otherAvatarUrl.value = resolveMaybeKeyToUrl(avatar)
        }
    }

    fun setOtherUserId(userId: String) {
        _otherUserId.value = userId
    }

    fun onTypingFromOther(value: Boolean) {
        _otherTyping.value = value
    }

    private fun makeMetaBody(replyToId: String?): RequestBody {
        val json = if (replyToId != null) {
            """{"replyToId":"$replyToId"}"""
        } else {
            "{}"
        }
        return json.toRequestBody("application/json".toMediaType())
    }

    private fun guessVideoExtension(mime: String): String = when (mime) {
        "video/mp4" -> "mp4"
        "video/3gpp" -> "3gp"
        "video/ogg" -> "ogv"
        "video/webm" -> "webm"
        else -> "mp4"
    }

    private fun looksLikeUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    private suspend fun resolveMaybeKeyToUrl(value: String?): String? {
        val v = value?.trim()
        if (v.isNullOrBlank()) return null
        if (looksLikeUrl(v)) return v

        signedUrlCache[v]?.let { return it }

        return runCatching {
            val resp = chatApi.viewFile(v)
            if (resp.isSuccessful) {
                val url = resp.body()?.url
                if (!url.isNullOrBlank()) {
                    signedUrlCache[v] = url
                    url
                } else null
            } else null
        }.getOrNull()
    }

    private suspend fun resolveAttachmentsUrls(input: List<ChatMessageDto>): List<ChatMessageDto> {
        if (input.isEmpty()) return input

        val out = ArrayList<ChatMessageDto>(input.size)
        for (msg in input) {
            val atts = msg.attachments
            if (atts.isNullOrEmpty()) {
                out.add(msg)
                continue
            }

            var changed = false
            val newAtts = atts.map { att ->
                val raw = att.url.trim()
                if (raw.isBlank() || looksLikeUrl(raw)) {
                    att
                } else {
                    val resolved = resolveMaybeKeyToUrl(raw)
                    if (!resolved.isNullOrBlank()) {
                        changed = true
                        att.copy(url = resolved)
                    } else {
                        att
                    }
                }
            }

            out.add(if (changed) msg.copy(attachments = newAtts) else msg)
        }
        return out
    }
}
