package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.ChatApi
import com.example.protren.network.ChatSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatsListViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)
    private val api by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(ChatApi::class.java)
    }

    private val _items = MutableStateFlow<List<ChatSummaryDto>>(emptyList())
    val items: StateFlow<List<ChatSummaryDto>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val resp = api.list() // ⬅️ poprawka: wcześniej było listMyChats()
                if (resp.isSuccessful) {
                    _items.value = resp.body().orEmpty()
                } else {
                    _error.value = "HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
            } finally {
                _loading.value = false
            }
        }
    }
}
