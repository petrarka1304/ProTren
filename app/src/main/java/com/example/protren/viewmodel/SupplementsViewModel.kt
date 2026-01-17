package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

sealed interface SupplementsUIState {
    data object Loading : SupplementsUIState
    data class Loaded(val today: List<Supplement>) : SupplementsUIState
    data class Error(val message: String) : SupplementsUIState
}

class SupplementsViewModel(
    private val prefs: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<SupplementsUIState>(SupplementsUIState.Loading)
    val state: StateFlow<SupplementsUIState> = _state

    private fun api(): SupplementApi? {
        val token = prefs.getAccessToken() ?: return null
        val refresh = prefs.getRefreshToken()
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { token },
            refreshTokenProvider = { refresh },
            onTokensUpdated = { newAccess, newRefresh ->
                prefs.saveToken(newAccess)
                if (!newRefresh.isNullOrBlank()) prefs.saveRefresh(newRefresh)
            },
            onUnauthorized = { prefs.clearAll() }
        )
        return retrofit.create(SupplementApi::class.java)
    }

    fun loadToday() {
        _state.value = SupplementsUIState.Loading
        viewModelScope.launch {
            val api = api() ?: run {
                _state.value = SupplementsUIState.Error("Brak tokena – zaloguj się ponownie.")
                return@launch
            }

            try {
                val res = withContext(Dispatchers.IO) { api.getToday() }
                if (res.isSuccessful) {
                    _state.value = SupplementsUIState.Loaded(res.body().orEmpty())
                } else {
                    _state.value = SupplementsUIState.Error("Błąd serwera: ${res.code()}")
                }
            } catch (e: IOException) {
                _state.value = SupplementsUIState.Error("Brak połączenia z siecią.")
            } catch (e: HttpException) {
                _state.value = SupplementsUIState.Error("Błąd HTTP: ${e.code()}")
            } catch (e: Exception) {
                _state.value = SupplementsUIState.Error(e.message ?: "Nieznany błąd")
            }
        }
    }

    fun toggleToday(id: String, take: Boolean, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val api = api() ?: return@launch done(false)
            try {
                val ok = withContext(Dispatchers.IO) {
                    val res = if (take) api.take(id) else api.undoTake(id)
                    res.isSuccessful
                }
                done(ok)
                if (ok) loadToday()
            } catch (_: Exception) {
                done(false)
            }
        }
    }
}
