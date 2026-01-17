package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log

class TrainerPanelViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)

    /** Retrofit z automatycznym refresh i obsługą 401 */
    private fun api(): TrainerAdminApi {
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { newAccess, newRefresh ->
                Log.d("TrainerVM", "Tokens refreshed OK")
                prefs.setTokens(newAccess, newRefresh)
            },
            onUnauthorized = {
                Log.w("TrainerVM", "Unauthorized – clearing session")
                prefs.clearAll()
            }
        )
        return retrofit.create(TrainerAdminApi::class.java)
    }

    // kiedyś: prośby o współpracę – teraz nie używamy, zostawiamy pustą listę
    private val _requests = MutableStateFlow<List<CoachingRequestItem>>(emptyList())
    val requests: StateFlow<List<CoachingRequestItem>> = _requests

    // lista podopiecznych trenera
    private val _trainees = MutableStateFlow<List<TraineeItem>>(emptyList())
    val trainees: StateFlow<List<TraineeItem>> = _trainees

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val a = api()

                // 🔹 NOWY FLOW: tylko lista podopiecznych
                val t = a.listTrainees()

                if (t.isSuccessful) {
                    _trainees.value = t.body().orEmpty()
                    _requests.value = emptyList() // na wszelki wypadek – zawsze pusto
                } else {
                    _error.value = "HTTP ${t.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
                Log.e("TrainerVM", "refresh error", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Kiedyś: respond(requestId, accept...) – prośby o coaching.
     * Teraz ta logika jest wyłączona, bo po opłaceniu suba użytkownik jest przypisywany automatycznie.
     * Zostawiamy pustą funkcję, żeby nie rozwalić miejsc, które ją ewentualnie wołają.
     */
    fun respond(requestId: String, accept: Boolean, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            // można pokazać prosty komunikat, że system próśb już nie działa:
            onDone(false, "System próśb do trenera jest wyłączony – przypisanie odbywa się automatycznie po płatności.")
        }
    }

    fun createPlanFor(
        userId: String,
        req: TrainerCreatePlanRequest,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = api().createPlanForUser(clientId = userId, body = req)
                if (res.isSuccessful) {
                    onDone(true, "Plan utworzony")
                } else {
                    onDone(false, "Błąd: ${res.code()}")
                }
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Błąd sieci")
                Log.e("TrainerVM", "createPlanFor error", e)
            }
        }
    }
}
