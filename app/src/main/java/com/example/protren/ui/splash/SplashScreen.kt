package com.example.protren.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.navigation.NavItem
import com.example.protren.network.ApiClient
import com.example.protren.network.RefreshApi
import com.example.protren.network.RefreshRequest
import com.example.protren.network.RoleApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SplashScreen(navController: NavController) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }

    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    val navRef = rememberUpdatedState(navController)
    var navigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val target = withContext(Dispatchers.IO) {
            val access = prefs.getAccessToken()
            val refresh = prefs.getRefreshToken()

            if (access.isNullOrBlank() && refresh.isNullOrBlank()) {
                return@withContext "login"
            }

            val refreshed = if (!refresh.isNullOrBlank()) {
                try {
                    val retrofit = ApiClient.create()
                    val api = retrofit.create(RefreshApi::class.java)
                    val resp = api.refresh(RefreshRequest(refresh!!))
                    if (resp.isSuccessful && resp.body() != null) {
                        val body = resp.body()!!
                        prefs.setTokens(body.accessToken, body.refreshToken)
                        true
                    } else false
                } catch (_: Exception) { false }
            } else false

            if (!refreshed) return@withContext "login"

            val roleFromMe: String? = withTimeoutOrNull(2500) {
                try {
                    val retrofitAuth = ApiClient.createWithAuth(
                        tokenProvider = { prefs.getAccessToken().orEmpty() },
                        onUnauthorized = { /* nic, fallback niżej */ }
                    )
                    val r = retrofitAuth.create(RoleApi::class.java).me()
                    if (r.isSuccessful) r.body()?.role else null
                } catch (_: Exception) { null }
            }

            val role = (roleFromMe ?: decodeRoleFromJwt(prefs.getAccessToken().orEmpty()))
                ?.lowercase()

            return@withContext when (role) {
                "trainer", "admin" -> "trainerRoot"
                "user", null -> NavItem.Home
                else -> NavItem.Home
            }
        }

        delay(120)

        if (!navigated && isActive) {
            navigated = true
            navRef.value.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(200))
                ) {
                    Text(
                        text = "ProTren",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator()
            }
        }
    }
}

private fun decodeRoleFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val json = String(android.util.Base64.decode(
            payload.replace('-', '+').replace('_', '/'),
            android.util.Base64.DEFAULT
        ))
        val regex = Regex("\"role\"\\s*:\\s*\"(.*?)\"")
        regex.find(json)?.groupValues?.getOrNull(1)
    } catch (_: Exception) {
        null
    }
}
