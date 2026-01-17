package com.example.protren.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.AvatarUploadResponse
import com.example.protren.network.FileViewResponse
import com.example.protren.network.UserProfileApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class ProfileAvatarViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private val retrofit by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        )
    }

    private val api by lazy { retrofit.create(UserProfileApi::class.java) }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl

    /**
     * Upload avatara pod Cloudflare R2.
     * Backend może zwrócić:
     * - avatarUrl (rzadziej / opcjonalnie)
     * - avatarKey (najczęściej)
     * - profile (czasem; profil może mieć już avatar url lub key)
     *
     * Docelowo zawsze ustawiamy _avatarUrl na SIGNED URL (żeby Coil/AsyncImage działał).
     */
    fun upload(uri: Uri, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val app = getApplication<Application>()

        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                val part = withContext(Dispatchers.IO) {
                    makeImagePart(app.contentResolver, uri, "avatar")
                }

                val meta: RequestBody =
                    """{"source":"android","folder":"avatars"}"""
                        .toRequestBody("application/json".toMediaTypeOrNull())

                val resp = api.uploadAvatar(part, meta)

                if (!resp.isSuccessful) {
                    _error.value = "HTTP ${resp.code()}"
                    onDone(false, null)
                    return@launch
                }

                val body: AvatarUploadResponse? = resp.body()

                // 1) jeśli backend zwróci avatarUrl -> użyj
                val directUrl = body?.avatarUrl
                if (!directUrl.isNullOrBlank()) {
                    _avatarUrl.value = directUrl
                    onDone(true, directUrl)
                    return@launch
                }

                // 2) jeśli backend zwróci profil i avatar jest już pełnym URL (lub backend zwraca już signed)
                val profileAvatar = body?.profile?.avatar
                if (!profileAvatar.isNullOrBlank() && looksLikeUrl(profileAvatar)) {
                    _avatarUrl.value = profileAvatar
                    onDone(true, profileAvatar)
                    return@launch
                }

                // 3) jeśli mamy avatarKey -> spróbuj rozwiązać na signed URL przez /api/files/view
                val key = body?.avatarKey ?: body?.profile?.avatar // czasem profil trzyma key w polu avatar
                if (!key.isNullOrBlank() && !looksLikeUrl(key)) {
                    val view = api.viewFile(key)
                    if (view.isSuccessful) {
                        val signedUrl: String? = view.body()?.url
                        if (!signedUrl.isNullOrBlank()) {
                            _avatarUrl.value = signedUrl
                            onDone(true, signedUrl)
                            return@launch
                        }
                    }
                }

                // 4) fallback: pobierz profil (backend u Ciebie podpisuje avatar na GET /api/profile)
                val refreshed = api.getProfile()
                if (refreshed.isSuccessful) {
                    val refreshedAvatar = refreshed.body()?.avatar
                    _avatarUrl.value = refreshedAvatar
                    onDone(true, refreshedAvatar)
                } else {
                    _error.value = "Upload OK, ale nie udało się odświeżyć profilu: HTTP ${refreshed.code()}"
                    onDone(false, null)
                }

            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
                onDone(false, null)
            } finally {
                _loading.value = false
            }
        }
    }

    // --- helpers ---

    private fun makeImagePart(cr: ContentResolver, uri: Uri, partName: String): MultipartBody.Part {
        val name = queryDisplayName(cr, uri) ?: "avatar.jpg"
        val mime = cr.getType(uri) ?: "image/jpeg"

        val cacheDir = getApplication<Application>().cacheDir
        val temp = File(cacheDir, "upload-${System.currentTimeMillis()}-$name")

        cr.openInputStream(uri).use { input ->
            FileOutputStream(temp).use { output ->
                requireNotNull(input) { "Nie udało się otworzyć pliku" }
                input.copyTo(output)
            }
        }

        val body = temp.asRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, name, body)
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        val cursor = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun looksLikeUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }
}
