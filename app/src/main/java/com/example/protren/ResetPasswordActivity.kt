package com.example.protren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.protren.data.UserPreferences
import com.example.protren.data.remote.AuthApi
import com.example.protren.data.remote.ResetPasswordRequest
import com.example.protren.network.ApiClient
import com.example.protren.ui.theme.ProTrenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResetPasswordActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // token z linku: protren://reset?token=XYZ
        val token = intent?.data?.getQueryParameter("token")

        setContent {
            ProTrenTheme {
                val snackbar = remember { SnackbarHostState() }
                val ctx = LocalContext.current
                val prefs = remember { UserPreferences(ctx) }

                val api: AuthApi = remember {
                    ApiClient.createWithAuth(
                        tokenProvider = { prefs.getAccessToken() },
                        refreshTokenProvider = { prefs.getRefreshToken() },
                        onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
                        onUnauthorized = { prefs.clearAll() }
                    ).create(AuthApi::class.java)
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    ResetPasswordScreen(
                        token = token,
                        padding = padding,
                        onSubmit = { newPassword, onDone ->
                            if (token.isNullOrBlank()) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    snackbar.showSnackbar("Nieprawidłowy token resetu.")
                                }
                                return@ResetPasswordScreen
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val resp = api.resetPassword(
                                        ResetPasswordRequest(
                                            token = token,
                                            password = newPassword
                                        )
                                    )
                                    CoroutineScope(Dispatchers.Main).launch {
                                        if (resp.isSuccessful) {
                                            val msg = resp.body()?.msg
                                                ?: "Hasło zostało zmienione. Możesz się zalogować."
                                            snackbar.showSnackbar(msg)
                                            onDone()
                                        } else {
                                            val msg = resp.errorBody()?.string()
                                                ?: "Nie udało się zmienić hasła."
                                            snackbar.showSnackbar(msg)
                                        }
                                    }
                                } catch (e: Exception) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        snackbar.showSnackbar("Błąd połączenia. Spróbuj ponownie.")
                                    }
                                }
                            }
                        },
                        onFinished = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
