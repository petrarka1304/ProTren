package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.MeResponse
import com.example.protren.network.ApiClient
import com.example.protren.network.TokenInterceptor
import com.example.protren.network.UserApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

sealed class AccountUIState {
    object Loading : AccountUIState()
    data class Loaded(val me: MeResponse) : AccountUIState()
    data class Saved(val msg: String? = null, val me: MeResponse? = null) : AccountUIState()
    data class Deleted(val msg: String? = null) : AccountUIState()
    data class Error(val message: String) : AccountUIState()
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)

    private val _state = MutableStateFlow<AccountUIState>(AccountUIState.Loading)
    val state: StateFlow<AccountUIState> = _state

    init {
        loadMe()
    }

    fun loadMe() {
        viewModelScope.launch {
            _state.value = AccountUIState.Loading
            try {
                val access = userPrefs.getAccessToken()
                if (access.isNullOrBlank()) {
                    _state.value = AccountUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { access }
                )
                val api = retrofit.create(UserApi::class.java)

                val res = api.getMe()
                if (res.isSuccessful && res.body() != null) {
                    _state.value = AccountUIState.Loaded(res.body()!!)
                } else if (res.code() == 401 || res.code() == 403) {
                    _state.value = AccountUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                } else {
                    _state.value = AccountUIState.Error("Błąd: ${res.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = AccountUIState.Error("Błąd ładowania danych konta: ${e.localizedMessage}")
            }
        }
    }

    fun saveName(firstName: String, lastName: String) {
        viewModelScope.launch {
            _state.value = AccountUIState.Loading
            try {
                val access = userPrefs.getAccessToken()
                if (access.isNullOrBlank()) {
                    _state.value = AccountUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { access }
                )
                val api = retrofit.create(UserApi::class.java)

                val body = mutableMapOf<String, String>()
                body["firstName"] = firstName
                body["lastName"] = lastName

                val res = api.updateMe(body)
                if (res.isSuccessful && res.body() != null) {
                    _state.value = AccountUIState.Saved(
                        msg = "Dane konta zaktualizowane ✅",
                        me = res.body()
                    )
                } else if (res.code() == 401 || res.code() == 403) {
                    _state.value = AccountUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                } else {
                    _state.value = AccountUIState.Error("Błąd zapisu: ${res.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = AccountUIState.Error("Błąd zapisu: ${e.localizedMessage}")
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _state.value = AccountUIState.Loading
            try {
                val access = userPrefs.getAccessToken()
                if (access.isNullOrBlank()) {
                    _state.value = AccountUIState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { access }
                )
                val api = retrofit.create(UserApi::class.java)

                val res = api.deleteMe()
                if (res.isSuccessful) {
                    // Wyczyść token w interceptorze (i ewentualnie w prefs – dopisz, jeśli masz taką metodę)
                    TokenInterceptor.clearToken()
                    // TODO: jeśli masz w UserPreferences metodę typu clearAll() / logout(), wywołaj ją tutaj.

                    _state.value = AccountUIState.Deleted("Konto zostało usunięte.")
                } else if (res.code() == 401 || res.code() == 403) {
                    _state.value = AccountUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                } else {
                    _state.value = AccountUIState.Error("Błąd usuwania konta: ${res.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = AccountUIState.Error("Błąd usuwania konta: ${e.localizedMessage}")
            }
        }
    }

    // helper
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
