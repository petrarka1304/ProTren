package com.example.protren.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UserPreferences(context: Context) {


    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("protren_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"

        private const val KEY_EMAIL = "user_email"
        private const val KEY_ROLE = "user_role"
        private const val KEY_NAME = "user_name"

        // śledzone ćwiczenia
        private const val KEY_TRACKED_EXERCISES = "tracked_exercises"
    }

    /*TOKENS*/

    fun saveToken(token: String?) {
        val normalized = normalizeToken(token)
        prefs.edit().putString(KEY_TOKEN, normalized ?: "").apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_TOKEN, null)
        ?.let { normalizeToken(it) }
        ?.takeIf { it.isNotBlank() }

    fun saveRefresh(token: String?) {
        val normalized = normalizeToken(token)
        prefs.edit().putString(KEY_REFRESH, normalized ?: "").apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
        ?.let { normalizeToken(it) }
        ?.takeIf { it.isNotBlank() }

    fun setTokens(access: String?, refresh: String?) {
        val normAccess = normalizeToken(access)
        val normRefresh = normalizeToken(refresh)

        prefs.edit()
            .putString(KEY_TOKEN, normAccess ?: "")
            .putString(KEY_REFRESH, normRefresh ?: "")
            .apply()
    }

    fun hasAccess(): Boolean = !getAccessToken().isNullOrBlank()
    fun hasRefresh(): Boolean = !getRefreshToken().isNullOrBlank()

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH)
            .apply()
    }

    /**Pełne czyszczenie*/
    fun clearAll() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_ROLE)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_TRACKED_EXERCISES)
            .apply()
    }
    fun isAccessExpired(): Boolean {
        val token = getAccessToken() ?: return true

        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true

            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE),
                Charsets.UTF_8
            )

            val payload = JSONObject(payloadJson)
            val expSeconds = payload.getLong("exp")

            val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            expSeconds <= nowSeconds
        } catch (e: Exception) {
            true
        }
    }
    /*DANE PROFILU */

    fun saveEmail(email: String?) {
        prefs.edit().putString(KEY_EMAIL, email ?: "").apply()
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveRole(role: String?) {
        prefs.edit().putString(KEY_ROLE, role ?: "user").apply()
    }

    fun getRole(): String = prefs.getString(KEY_ROLE, "user") ?: "user"

    fun saveName(name: String?) {
        prefs.edit().putString(KEY_NAME, name ?: "").apply()
    }

    fun getName(): String? = prefs.getString(KEY_NAME, null)

    fun saveUserId(id: String?) {
        prefs.edit().putString(KEY_USER_ID, id ?: "").apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    /*ŚLEDZONE ĆWICZENIA*/

    fun saveTrackedExercises(names: List<String>) {
        val clean = names
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        prefs.edit().putStringSet(KEY_TRACKED_EXERCISES, clean.toSet()).apply()
    }

    fun getTrackedExercises(): List<String> {
        return prefs.getStringSet(KEY_TRACKED_EXERCISES, emptySet())
            ?.toList()
            ?.sortedBy { it.lowercase() }
            ?: emptyList()
    }

    fun clearTrackedExercises() {
        prefs.edit().remove(KEY_TRACKED_EXERCISES).apply()
    }

    /*ZGODNOŚĆ WSTECZNA*/

    @Deprecated("Użyj getAccessToken()", ReplaceWith("getAccessToken()"))
    fun getToken(): String? = getAccessToken()

    @Deprecated("Użyj clearTokens()", ReplaceWith("clearTokens()"))
    fun clearToken() = clearTokens()

    @Deprecated("Użyj getRefreshToken()", ReplaceWith("getRefreshToken()"))
    fun getRefresh(): String? = getRefreshToken()

    
    fun getAccessExpEpochSeconds(): Long? {
        val token = getAccessToken() ?: return null
        val parts = token.split(".")
        if (parts.size < 2) return null

        return try {
            val payloadPart = parts[1]
            val payloadJson = decodeBase64UrlToString(payloadPart) ?: return null
            val regex = Regex("""\"exp\"\s*:\s*(\d+)""")
            val match = regex.find(payloadJson) ?: return null
            match.groupValues[1].toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /* ───────────── helpers ───────────── */

    private fun normalizeToken(token: String?): String? {
        if (token == null) return null
        return token
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
    }

    private fun decodeBase64UrlToString(input: String): String? {
        return try {
            val pad = (4 - input.length % 4) % 4
            val fixed = input
                .replace('-', '+')
                .replace('_', '/')
                .plus("=".repeat(pad))

            val bytes = Base64.decode(fixed, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
