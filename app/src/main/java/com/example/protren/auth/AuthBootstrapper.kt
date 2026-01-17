package com.example.protren.auth

import android.content.Context
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.RefreshApi
import com.example.protren.network.RefreshRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Uruchamiaj na starcie (np. w Splash/StartUp):
 * - jeśli ACCESS wygasł albo go brak → spróbuje odświeżyć przez REFRESH (bez Authorization)
 * - zapisze nowe tokeny, albo wyczyści preferencje i zwróci false
 */
object AuthBootstrapper {

    suspend fun ensureFreshTokens(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = UserPreferences(context)

        val needsRefresh = !prefs.hasAccess() || prefs.isAccessExpired()
        if (!needsRefresh) return@withContext true

        val refreshToken = prefs.getRefreshToken()?.trim()
        if (refreshToken.isNullOrEmpty()) {
            prefs.clearAll()
            return@withContext false
        }

        // Refresh powinien iść klientem BEZ auth-interceptora
        val retrofit = ApiClient.create()
        val api = retrofit.create(RefreshApi::class.java)

        return@withContext try {
            val resp = api.refresh(RefreshRequest(refreshToken))

            if (!resp.isSuccessful) {
                // typowo: 401/403 gdy refresh nieważny
                prefs.clearAll()
                return@withContext false
            }

            val body = resp.body()
            if (body == null) {
                prefs.clearAll()
                return@withContext false
            }

            val newAccess = body.accessToken?.trim()
            val newRefresh = body.refreshToken?.trim()

            if (newAccess.isNullOrEmpty()) {
                // bez accessa nie ma sensu kontynuować
                prefs.clearAll()
                return@withContext false
            }

            // jeśli backend nie zwróci nowego refreshToken, zostaw stary
            val finalRefresh = if (!newRefresh.isNullOrEmpty()) newRefresh else refreshToken

            prefs.setTokens(newAccess, finalRefresh)
            true
        } catch (e: HttpException) {
            // 4xx/5xx
            prefs.clearAll()
            false
        } catch (e: Throwable) {
            // brak sieci, timeout itp.
            // możesz rozważyć: NIE czyścić tokenów przy błędach sieci,
            // ale bezpiecznie zostawiam jak było u Ciebie: clearAll()
            prefs.clearAll()
            false
        }
    }
}
