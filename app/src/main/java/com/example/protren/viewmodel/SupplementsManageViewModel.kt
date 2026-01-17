package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
import com.example.protren.repository.SupplementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SupplementsManageUIState {
    data object Loading : SupplementsManageUIState
    data class Loaded(val items: List<Supplement>) : SupplementsManageUIState
    data class Saved(val message: String) : SupplementsManageUIState
    data class Error(val message: String) : SupplementsManageUIState
}

class SupplementsManageViewModel(
    private val prefs: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<SupplementsManageUIState>(SupplementsManageUIState.Loading)
    val state: StateFlow<SupplementsManageUIState> = _state

    private fun repo(): SupplementRepository? {
        val token = prefs.getAccessToken() ?: return null
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { token },
            onUnauthorized = { prefs.clearTokens() }
        )
        val api = retrofit.create(SupplementApi::class.java)
        return SupplementRepository(api)
    }

    fun loadAll() {
        _state.value = SupplementsManageUIState.Loading
        viewModelScope.launch {
            val r = repo() ?: run {
                _state.value = SupplementsManageUIState.Error("Brak tokena – zaloguj się ponownie.")
                return@launch
            }
            val res = r.getAll()
            _state.value = if (res.isSuccessful) {
                SupplementsManageUIState.Loaded(res.body().orEmpty())
            } else {
                SupplementsManageUIState.Error("Błąd: ${res.code()}")
            }
        }
    }

    fun create(s: Supplement) {
        viewModelScope.launch {
            val r = repo() ?: return@launch
            val res = r.create(s)
            _state.value = if (res.isSuccessful) {
                SupplementsManageUIState.Saved("Utworzono")
            } else SupplementsManageUIState.Error("Błąd: ${res.code()}")
        }
    }

    fun update(id: String, s: Supplement) {
        viewModelScope.launch {
            val r = repo() ?: return@launch
            val res = r.update(id, s)
            _state.value = if (res.isSuccessful) {
                SupplementsManageUIState.Saved("Zaktualizowano")
            } else SupplementsManageUIState.Error("Błąd: ${res.code()}")
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val r = repo() ?: return@launch
            val res = r.delete(id)
            _state.value = if (res.isSuccessful) {
                SupplementsManageUIState.Saved("Usunięto")
            } else SupplementsManageUIState.Error("Błąd: ${res.code()}")
        }
    }
}
