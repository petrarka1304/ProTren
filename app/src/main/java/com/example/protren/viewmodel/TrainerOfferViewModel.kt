package com.example.protren.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Trainer
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainerApi
import com.example.protren.network.TrainerUpsertRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class TrainerOfferViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private var working: OfferUi = OfferUi()

    private var pendingAvatarUri: Uri? = null
    private var pendingGalleryUris: List<Uri> = emptyList()

    sealed class State {
        object Loading : State()
        data class Loaded(val offer: OfferUi) : State()
        data class Error(val message: String) : State()
    }

    fun load() {
        _state.value = State.Loading

        viewModelScope.launch {
            try {
                val prefs = UserPreferences(getApplication())
                val token = prefs.getAccessToken().orEmpty()

                val api = ApiClient
                    .createWithAuth(tokenProvider = { token })
                    .create(TrainerApi::class.java)

                val res = api.getMyOffer()

                when {
                    res.isSuccessful -> {
                        val trainer = res.body()
                        working = trainer?.toUi() ?: OfferUi()
                        _state.value = State.Loaded(working)
                    }

                    res.code() == 400 || res.code() == 404 -> {
                        working = OfferUi()
                        _state.value = State.Loaded(working)
                    }

                    else -> {
                        _state.value = State.Error("Błąd serwera: ${res.code()} ${res.message()}")
                    }
                }
            } catch (e: Exception) {
                _state.value = State.Error(e.localizedMessage ?: "Błąd sieci")
            }
        }
    }
    fun updateFrom(ui: OfferUi) {
        working = ui
        _state.value = State.Loaded(working)
    }


    suspend fun uploadImages(
        context: Context,
        avatarUri: Uri?,
        galleryUris: List<Uri>
    ): Boolean {
        if (avatarUri == null && galleryUris.isEmpty()) return true

        return try {
            val prefs = UserPreferences(getApplication())
            val token = prefs.getAccessToken().orEmpty()

            val api = ApiClient
                .createWithAuth(tokenProvider = { token })
                .create(TrainerApi::class.java)

            var newAvatarUrl: String? = working.avatarUrl
            val newGalleryUrls = (working.galleryUrls ?: emptyList()).toMutableList()

            if (avatarUri != null) {
                val avatarPart = createImagePart(
                    context = context,
                    uri = avatarUri,
                    partName = "avatar",
                    fileName = "trainer_avatar.jpg"
                )

                if (avatarPart != null) {
                    val res = api.uploadTrainerAvatar(avatarPart)
                    if (res.isSuccessful) {
                        val body = res.body()
                        if (!body?.url.isNullOrBlank()) {
                            newAvatarUrl = body!!.url
                        }
                    } else {
                        _state.value = State.Error(
                            "Nie udało się wysłać avatara: ${res.code()} ${res.message()}"
                        )
                        return false
                    }
                }
            }

            if (galleryUris.isNotEmpty()) {
                val parts = mutableListOf<MultipartBody.Part>()
                galleryUris.forEachIndexed { index, uri ->
                    val part = createImagePart(
                        context = context,
                        uri = uri,
                        partName = "images",
                        fileName = "portfolio_$index.jpg"
                    )
                    if (part != null) parts.add(part)
                }

                if (parts.isNotEmpty()) {
                    val res = api.uploadTrainerGallery(parts)
                    if (res.isSuccessful) {
                        val body = res.body()
                        val urls = body?.urls.orEmpty()
                        if (urls.isNotEmpty()) {
                            newGalleryUrls.clear()
                            newGalleryUrls.addAll(urls)
                        }
                    } else {
                        _state.value = State.Error(
                            "Nie udało się wysłać zdjęć portfolio: ${res.code()} ${res.message()}"
                        )
                        return false
                    }
                }
            }

            working = working.copy(
                avatarUrl = newAvatarUrl,
                galleryUrls = if (newGalleryUrls.isEmpty()) null else newGalleryUrls
            )
            _state.value = State.Loaded(working)

            pendingAvatarUri = null
            pendingGalleryUris = emptyList()

            true
        } catch (e: Exception) {
            _state.value = State.Error(e.localizedMessage ?: "Błąd przy uploadzie zdjęć")
            false
        }
    }

    suspend fun save(): Boolean {
        return try {
            val prefs = UserPreferences(getApplication())
            val token = prefs.getAccessToken().orEmpty()

            val api = ApiClient
                .createWithAuth(tokenProvider = { token })
                .create(TrainerApi::class.java)

            val dto = TrainerUpsertRequest(
                name = working.name.orEmpty(),
                email = working.email.orEmpty(),
                bio = working.bio.orEmpty(),
                specialties = working.specialties,
                priceMonth = working.priceMonth,
                avatarUrl = working.avatarUrl,
                galleryUrls = working.galleryUrls
            )

            val res = api.upsertMyOffer(dto)

            if (res.isSuccessful) {
                val trainer = res.body()
                working = trainer?.toUi() ?: working
                pendingAvatarUri = null
                pendingGalleryUris = emptyList()
                _state.value = State.Loaded(working)
                true
            } else {
                _state.value = State.Error("Nie udało się zapisać: ${res.code()} ${res.message()}")
                false
            }
        } catch (e: Exception) {
            _state.value = State.Error(e.localizedMessage ?: "Błąd sieci przy zapisie")
            false
        }
    }

    fun onAvatarSelected(context: Context, uri: Uri?) {
        pendingAvatarUri = uri
    }

    fun onAvatarCleared() {
        pendingAvatarUri = null
        working = working.copy(avatarUrl = null)
        _state.value = State.Loaded(working)
    }

    fun onGalleryAdded(context: Context, uris: List<Uri>) {
        pendingGalleryUris = uris
    }

    fun onGalleryRemoved(index: Int) {
        if (index in pendingGalleryUris.indices) {
            pendingGalleryUris = pendingGalleryUris.toMutableList().also { it.removeAt(index) }
        }

        val currentUrls = working.galleryUrls?.toMutableList() ?: mutableListOf()
        if (index in currentUrls.indices) {
            currentUrls.removeAt(index)
            working = working.copy(
                galleryUrls = if (currentUrls.isEmpty()) null else currentUrls
            )
            _state.value = State.Loaded(working)
        }
    }

    private fun Trainer.toUi() = OfferUi(
        name = name,
        email = email,
        bio = bio,
        specialties = this.specialties ?: emptyList(),
        priceMonth = this.priceMonth,
        avatarUrl = this.avatarUrl,
        galleryUrls = try { this.galleryUrls } catch (_: Throwable) { null }
    )

    data class OfferUi(
        val name: String? = null,
        val email: String? = null,
        val bio: String? = null,
        val specialties: List<String> = emptyList(),
        val priceMonth: Double? = null,
        val avatarUrl: String? = null,
        val galleryUrls: List<String>? = null
    )

    data class FileUploadResponse(
        val url: String
    )

    data class GalleryUploadResponse(
        val urls: List<String> = emptyList()
    )

    private suspend fun createImagePart(
        context: Context,
        uri: Uri,
        partName: String,
        fileName: String
    ): MultipartBody.Part? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val input = cr.openInputStream(uri) ?: return@withContext null
        val bytes = input.use { it.readBytes() }

        val mimeType = cr.getType(uri) ?: "image/*"
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())

        MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }
}
