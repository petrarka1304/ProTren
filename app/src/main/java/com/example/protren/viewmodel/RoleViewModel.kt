package com.example.protren.viewmodel

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.RoleApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AppRole { TRAINEE, TRAINER }


class RoleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)

    private val _role = MutableStateFlow<AppRole?>(null)
    val role: StateFlow<AppRole?> = _role

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private fun mapRole(source: String?): AppRole? = when (source?.lowercase()) {
        "trainer", "admin" -> AppRole.TRAINER
        "user"             -> AppRole.TRAINEE
        else               -> null
    }

    private fun cleanToken(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.removePrefix("Bearer ").removePrefix("bearer ").trim()
    }

    private fun decodeRoleFromJwt(token: String?): String? {
        val t = token?.trim().orEmpty()
        if (t.isBlank()) return null
        return try {
            val parts = t.split(".")
            if (parts.size < 2) return null
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            )
            val json = JSONObject(payload)
            json.optString("role", null)
        } catch (e: Exception) {
            Log.w("RoleVM", "JWT decode failed: ${e.message}")
            null
        }
    }

    fun load(silent: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            if (!silent) _error.value = null
            try {
                val raw = prefs.getAccessToken()
                val token = cleanToken(raw)

                if (token.isBlank()) {
                    if (_role.value == null) _role.value = AppRole.TRAINEE
                    Log.d("RoleVM", "No token — defaulting to TRAINEE (silent).")
                    return@launch
                }

                decodeRoleFromJwt(token)?.let { jwtRole ->
                    mapRole(jwtRole)?.let { mapped ->
                        _role.value = mapped
                        Log.d("RoleVM", "Role from JWT: $jwtRole → $mapped")
                    }
                } ?: run {
                    Log.d("RoleVM", "No/unknown role in JWT — will confirm via /users/me")
                }

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { prefs.getAccessToken() },
                    refreshTokenProvider = { prefs.getRefreshToken() },
                    onTokensUpdated = { newAccess, newRefresh ->
                        prefs.setTokens(newAccess, newRefresh)
                        Log.d("RoleVM", "Tokens refreshed while confirming role.")
                    },
                    onUnauthorized = {
                        prefs.clearAll()
                        Log.w("RoleVM", "Unauthorized while confirming role — session cleared.")
                    }
                )

                val api = retrofit.create(RoleApi::class.java)
                val res = api.me()
                if (res.isSuccessful) {
                    val serverRole = res.body()?.role?.lowercase()
                    val mapped = mapRole(serverRole)
                    if (mapped != null) {
                        _role.value = mapped
                        Log.d("RoleVM", "Role from /users/me: $serverRole → $mapped")
                    } else {
                        Log.w("RoleVM", "Unknown server role: $serverRole — keeping previous=${_role.value}")
                    }
                } else {
                    Log.w("RoleVM", "users/me failed: HTTP ${res.code()} — keeping previous=${_role.value}")
                }
            } catch (e: Exception) {
                Log.e("RoleVM", "load error: ${e.message}", e)
                if (!silent) _error.value = e.localizedMessage
                if (_role.value == null) _role.value = AppRole.TRAINEE
            } finally {
                _loading.value = false
            }
        }
    }
}
