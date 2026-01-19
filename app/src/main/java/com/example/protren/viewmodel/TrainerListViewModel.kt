package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Trainer
import com.example.protren.network.ApiClient
import com.example.protren.network.CoachingRequestBody
import com.example.protren.network.TrainerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

class TrainerListViewModel(app: Application) : AndroidViewModel(app) {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _items = MutableStateFlow<List<Trainer>>(emptyList())
    val items: StateFlow<List<Trainer>> = _items

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val token = UserPreferences(getApplication()).getAccessToken().orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainerApi::class.java)
                val res = api.listTrainers()
                if (res.isSuccessful) _items.value = res.body().orEmpty()
                else _error.value = "HTTP ${res.code()} – ${res.errorMsg()}"
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            } finally { _loading.value = false }
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
