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

sealed interface SupplementsTodayUIState {
    data object Loading : SupplementsTodayUIState
    data class Loaded(val items: List<Supplement>) : SupplementsTodayUIState
    data class Error(val message: String) : SupplementsTodayUIState
}

class SupplementsTodayViewModel(
    private val prefs: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<SupplementsTodayUIState>(SupplementsTodayUIState.Loading)
    val state: StateFlow<SupplementsTodayUIState> = _state

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
        _state.value = SupplementsTodayUIState.Loading
        viewModelScope.launch {
            val api = api() ?: run {
                _state.value = SupplementsTodayUIState.Error("Brak tokena – zaloguj się ponownie.")
                return@launch
            }

            try {
                val res = withContext(Dispatchers.IO) { api.getToday() }
                if (res.isSuccessful) {
                    _state.value = SupplementsTodayUIState.Loaded(res.body().orEmpty())
                } else {
                    _state.value = SupplementsTodayUIState.Error("Błąd serwera: ${res.code()}")
                }
            } catch (e: IOException) {
                _state.value = SupplementsTodayUIState.Error("Brak połączenia z siecią.")
            } catch (e: HttpException) {
                _state.value = SupplementsTodayUIState.Error("Błąd HTTP: ${e.code()}")
            } catch (e: Exception) {
                _state.value = SupplementsTodayUIState.Error(e.message ?: "Nieznany błąd")
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
