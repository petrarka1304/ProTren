package com.example.protren.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.data.remote.AuthApi
import com.example.protren.data.remote.LoginRequest
import com.example.protren.data.remote.LoginResponse
import com.example.protren.network.ApiClient
import com.example.protren.network.TokenInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

private const val TAG = "LoginVM"

class LoginViewModel(private val prefs: UserPreferences) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(email: String, password: String) {
        _state.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.create().create(AuthApi::class.java)

                val res: Response<LoginResponse> = withContext(Dispatchers.IO) {
                    api.login(LoginRequest(email.trim(), password))
                }

                if (res.isSuccessful) {
                    val body = res.body()
                    if (body == null) {
                        _state.value = LoginState.Error("Pusta odpowiedź serwera")
                        return@launch
                    }

                    val token = cleanToken(body.token)
                    if (token.isBlank()) {
                        _state.value = LoginState.Error("Brak tokena w odpowiedzi")
                        return@launch
                    }

                    Log.d(
                        TAG,
                        "Login OK. tokenLen=${token.length}, role=${body.user.role}, email=${body.user.email}"
                    )

                    // Zapis do SharedPreferences
                    prefs.saveToken(token)
                    prefs.saveRole(body.user.role.lowercase())
                    prefs.saveEmail(body.user.email)

                    // Aktualizacja tokenu w pamięci (dla interceptora)
                    TokenInterceptor.updateToken(token)

                    _state.value = LoginState.Success
                } else {
                    val msg = "HTTP ${res.code()} ${res.errorMsg()}"
                    Log.w(TAG, "Login failed: $msg")
                    _state.value = LoginState.Error(msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error on login: ${e.localizedMessage}", e)
                _state.value = LoginState.Error("Błąd sieci: ${e.localizedMessage}")
            }
        }
    }

    private fun Response<*>.errorMsg(): String {
        return try {
            val t = errorBody()?.string()
            if (t.isNullOrBlank()) message()
            else JSONObject(t).optString("msg", message())
        } catch (_: Exception) {
            message()
        }
    }

    private fun cleanToken(raw: String?): String =
        (raw ?: "").removePrefix("Bearer ").removePrefix("bearer ").trim()
}
