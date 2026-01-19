package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.WeeklySummaryResponse
import com.example.protren.network.WorkoutApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AnalyticsState {
    object Loading : AnalyticsState()
    data class Ready(val data: WeeklySummaryResponse) : AnalyticsState()
    data class Error(val message: String) : AnalyticsState()
}

enum class AnalyticsRange(val days: Int, val label: String) {
    D7(7, "7 dni"), D14(14, "14 dni"), D30(30, "30 dni")
}

class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private val _state = MutableStateFlow<AnalyticsState>(AnalyticsState.Loading)
    val state: StateFlow<AnalyticsState> = _state

    private val _range = MutableStateFlow(AnalyticsRange.D7)
    val range: StateFlow<AnalyticsRange> = _range

    fun setRange(newRange: AnalyticsRange) {
        if (_range.value != newRange) {
            _range.value = newRange
            viewModelScope.launch { fetch() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AnalyticsState.Loading
            fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        try {
            val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
            val api = ApiClient
                .createWithAuth(tokenProvider = { token })
                .create(WorkoutApi::class.java)

            val currentRange = _range.value

            val res = withContext(Dispatchers.IO) {
                api.getSummaryByDays(currentRange.days)
            }

            if (res.isSuccessful) {
                _state.value = AnalyticsState.Ready(res.body() ?: WeeklySummaryResponse())
            } else {
                _state.value = AnalyticsState.Error("Błąd pobierania: HTTP ${res.code()}")
            }
        } catch (t: Throwable) {
            _state.value = AnalyticsState.Error("Błąd sieci: ${t.localizedMessage ?: "nieznany"}")
        }
    }
}
