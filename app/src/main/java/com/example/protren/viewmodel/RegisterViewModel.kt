package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.data.remote.AuthApi
import com.example.protren.data.remote.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    object Success : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(private val prefs: UserPreferences) : ViewModel() {

    private val _state = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val state: StateFlow<RegisterState> = _state

    fun register(email: String, password: String, isTrainer: Boolean) {
        _state.value = RegisterState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.create().create(AuthApi::class.java)
                val role = if (isTrainer) "trainer" else "user"
                val res = api.register(
                    RegisterRequest(
                        email = email.trim(),
                        password = password,
                        role = role
                    )
                )

                if (res.isSuccessful) {
                    _state.value = RegisterState.Success
                } else {
                    _state.value = RegisterState.Error("HTTP ${res.code()} ${res.errorMsg()}")
                }
            } catch (e: Exception) {
                _state.value = RegisterState.Error("Błąd sieci: ${e.localizedMessage}")
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
}
