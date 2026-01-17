package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Trainer
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface TrainerProfileState {
    object Loading : TrainerProfileState
    data class Ready(val data: Trainer) : TrainerProfileState
    data class Error(val message: String) : TrainerProfileState
}

class TrainerProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app.applicationContext)

    private val _state = MutableStateFlow<TrainerProfileState>(TrainerProfileState.Loading)
    val state: StateFlow<TrainerProfileState> = _state

    private fun api(): TrainerApi {
        val tokenProvider = { prefs.getAccessToken().orEmpty() }
        return ApiClient.createWithAuth(
            tokenProvider = tokenProvider,
            onUnauthorized = { /* TODO: obsłuż 401 jeśli chcesz */ }
        ).create(TrainerApi::class.java)
    }

    fun loadPublic(trainerId: String) {
        viewModelScope.launch {
            _state.value = TrainerProfileState.Loading
            try {
                val res = api().getTrainer(trainerId)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) _state.value = TrainerProfileState.Ready(body)
                    else _state.value = TrainerProfileState.Error("Brak danych profilu")
                } else {
                    _state.value = TrainerProfileState.Error("Błąd ${res.code()}")
                }
            } catch (_: IOException) {
                _state.value = TrainerProfileState.Error("Brak połączenia")
            } catch (_: HttpException) {
                _state.value = TrainerProfileState.Error("Błąd serwera")
            } catch (e: Exception) {
                _state.value = TrainerProfileState.Error(e.message ?: "Nieznany błąd")
            }
        }
    }

    fun loadMine() {
        viewModelScope.launch {
            _state.value = TrainerProfileState.Loading
            try {
                val res = api().getMyOffer()
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) _state.value = TrainerProfileState.Ready(body)
                    else _state.value = TrainerProfileState.Error("Brak danych profilu")
                } else {
                    _state.value = TrainerProfileState.Error("Błąd ${res.code()}")
                }
            } catch (_: IOException) {
                _state.value = TrainerProfileState.Error("Brak połączenia")
            } catch (e: Exception) {
                _state.value = TrainerProfileState.Error(e.message ?: "Nieznany błąd")
            }
        }
    }
}
