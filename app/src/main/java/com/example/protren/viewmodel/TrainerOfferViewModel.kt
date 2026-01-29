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
import com.example.protren.network.UserApi
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

    // Funkcja pomocnicza do tworzenia API
    private fun getApi(): TrainerApi {
        val prefs = UserPreferences(getApplication())
        return ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken().orEmpty() },
            refreshTokenProvider = { prefs.getRefreshToken().orEmpty() },
            onTokensUpdated = { acc, ref -> prefs.setTokens(acc, ref) }
        ).create(TrainerApi::class.java)
    }

    fun load() {
        _state.value = State.Loading

        viewModelScope.launch {
            try {
                val api = getApi()
                val res = api.getMyOffer()

                if (res.isSuccessful) {
                    val trainer = res.body()
                    working = trainer?.toUi() ?: OfferUi()
                    _state.value = State.Loaded(working)
                } else if (res.code() == 400 || res.code() == 404) {
                    working = OfferUi()
                    _state.value = State.Loaded(working)
                } else {
                    _state.value = State.Error("Błąd serwera: ${res.code()}")
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

    suspend fun save(): Boolean {
        return try {
            val api = getApi()
            val userApi = ApiClient.createWithAuth(
                tokenProvider = { UserPreferences(getApplication()).getAccessToken().orEmpty() }
            ).create(UserApi::class.java)

            val fullName = working.name.orEmpty().trim()
            val parts = fullName.split(" ", limit = 2)
            val fName = parts.getOrNull(0) ?: ""
            val lName = parts.getOrNull(1) ?: ""

            val userUpdateMap = mapOf(
                "firstName" to fName,
                "lastName" to lName
            )
            userApi.updateMe(userUpdateMap)

            val dto = TrainerUpsertRequest(
                name = fullName,
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
                _state.value = State.Loaded(working)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadImages(context: Context, avatarUri: Uri?, galleryUris: List<Uri>): Boolean {
        if (avatarUri == null && galleryUris.isEmpty()) return true
        return try {
            val api = getApi()
            var newAvatarUrl: String? = working.avatarUrl
            val newGalleryUrls = (working.galleryUrls ?: emptyList()).toMutableList()

            if (avatarUri != null) {
                val part = createImagePart(context, avatarUri, "avatar", "avatar.jpg")
                if (part != null) {
                    val res = api.uploadTrainerAvatar(part)
                    if (res.isSuccessful) newAvatarUrl = res.body()?.url
                }
            }

            if (galleryUris.isNotEmpty()) {
                val parts = galleryUris.mapIndexedNotNull { i, uri ->
                    createImagePart(context, uri, "images", "img_$i.jpg")
                }
                val res = api.uploadTrainerGallery(parts)
                if (res.isSuccessful) {
                    newGalleryUrls.clear()
                    newGalleryUrls.addAll(res.body()?.urls.orEmpty())
                }
            }

            working = working.copy(avatarUrl = newAvatarUrl, galleryUrls = newGalleryUrls)
            true
        } catch (e: Exception) { false }
    }


    private fun Trainer.toUi() = OfferUi(
        name = name,
        email = email,
        bio = bio,
        specialties = specialties ?: emptyList(),
        priceMonth = priceMonth,
        avatarUrl = avatarUrl,
        galleryUrls = galleryUrls
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

    fun onAvatarSelected(context: Context, uri: Uri?) { pendingAvatarUri = uri }
    fun onAvatarCleared() {
        working = working.copy(avatarUrl = null)
        _state.value = State.Loaded(working)
    }
    fun onGalleryAdded(context: Context, uris: List<Uri>) { pendingGalleryUris = uris }
    fun onGalleryRemoved(index: Int) {
        val current = working.galleryUrls?.toMutableList() ?: mutableListOf()
        if (index in current.indices) {
            current.removeAt(index)
            working = working.copy(galleryUrls = if (current.isEmpty()) null else current)
            _state.value = State.Loaded(working)
        }
    }

    private suspend fun createImagePart(context: Context, uri: Uri, partName: String, fileName: String): MultipartBody.Part? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val requestBody = bytes.toRequestBody((context.contentResolver.getType(uri) ?: "image/*").toMediaTypeOrNull())
            MultipartBody.Part.createFormData(partName, fileName, requestBody)
        } catch (e: Exception) { null }
    }
}