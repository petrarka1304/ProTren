package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.UserProfile
import com.example.protren.network.ApiClient
import com.example.protren.network.UserProfileApi
import com.example.protren.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

sealed class ProfileUIState {
    object Loading : ProfileUIState()
    data class Loaded(val profile: UserProfile) : ProfileUIState()
    data class Saved(val info: String? = null) : ProfileUIState()
    data class Error(val message: String) : ProfileUIState()
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)

    private val _state = MutableStateFlow<ProfileUIState>(ProfileUIState.Loading)
    val state: StateFlow<ProfileUIState> = _state

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _state.value = ProfileUIState.Loading
            try {
                val token = userPrefs.getAccessToken()
                if (token.isNullOrBlank()) {
                    _state.value = ProfileUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }
                val api = ApiClient.createWithAuth({ token }).create(UserProfileApi::class.java)
                val repository = UserProfileRepository(api)

                val res = repository.getProfile()
                when {
                    res.isSuccessful -> _state.value = ProfileUIState.Loaded(res.body() ?: UserProfile())
                    res.code() == 404 -> _state.value = ProfileUIState.Loaded(UserProfile())
                    res.code() == 401 || res.code() == 403 ->
                        _state.value = ProfileUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                    else -> _state.value = ProfileUIState.Error("Błąd: ${res.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = ProfileUIState.Error("Błąd ładowania profilu: ${e.localizedMessage}")
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _state.value = ProfileUIState.Loading
            try {
                val token = userPrefs.getAccessToken()
                if (token.isNullOrBlank()) {
                    _state.value = ProfileUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }
                val api = ApiClient.createWithAuth({ token }).create(UserProfileApi::class.java)
                val repository = UserProfileRepository(api)

                val res: Response<*> = repository.saveMyProfile(profile)
                if (!res.isSuccessful) {
                    _state.value = ProfileUIState.Error("Błąd zapisu profilu: ${res.errorMsg()}")
                    return@launch
                }

                val nutr = runCatching { repository.calculateNutrition(profile) }.getOrNull()
                val info: String? = when {
                    nutr == null -> "Profil zapisany. (Brak odpowiedzi kalkulacji – spróbuj ponownie później.)"
                    nutr.isSuccessful -> "Profil zapisany. Kalkulacje żywienia zaktualizowane."
                    nutr.code() == 401 || nutr.code() == 403 -> "Profil zapisany, ale sesja wygasła w trakcie kalkulacji."
                    else -> "Profil zapisany. (Kalkulacje: ${nutr.errorMsg()})"
                }

                _state.value = ProfileUIState.Saved(info)
                loadProfile()
            } catch (e: Exception) {
                _state.value = ProfileUIState.Error("Błąd zapisu profilu: ${e.localizedMessage}")
            }
        }
    }
    fun calculateNow(profile: UserProfile) {
        viewModelScope.launch {
            _state.value = ProfileUIState.Loading
            try {
                val token = userPrefs.getAccessToken()
                if (token.isNullOrBlank()) {
                    _state.value = ProfileUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }
                val api = ApiClient.createWithAuth({ token }).create(UserProfileApi::class.java)
                val repository = UserProfileRepository(api)

                val nutr = repository.calculateNutrition(profile)
                if (nutr.isSuccessful) {
                    _state.value = ProfileUIState.Saved("Kalkulacje żywienia zaktualizowane ✅")
                    loadProfile()
                } else if (nutr.code() == 401 || nutr.code() == 403) {
                    _state.value = ProfileUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                } else {
                    _state.value = ProfileUIState.Error("Błąd kalkulacji: ${nutr.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = ProfileUIState.Error("Błąd kalkulacji: ${e.localizedMessage}")
            }
        }
    }

    private fun Response<*>.errorMsg(): String {
        return try {
            val body = errorBody()?.string()
            if (body.isNullOrBlank()) message()
            else JSONObject(body).optString("msg", message())
        } catch (_: Exception) {
            message()
        }
    }
}
