@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.login

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.data.remote.AuthApi
import com.example.protren.data.remote.ForgotPasswordRequest
import com.example.protren.navigation.NavItem
import com.example.protren.network.ApiClient
import com.example.protren.viewmodel.LoginState
import com.example.protren.viewmodel.LoginViewModel
import com.example.protren.viewmodel.LoginViewModelFactory
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun LoginScreen(navController: NavController) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    val vm: LoginViewModel = remember { LoginViewModelFactory(prefs).create(LoginViewModel::class.java) }

    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotLoading by remember { mutableStateOf(false) }

    val authApi: AuthApi = remember {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(AuthApi::class.java)
    }
    BackHandler(enabled = true) {
        // Zamiast cofać aplikację – zamknij aplikację
        (ctx as? Activity)?.finish()
    }

    LaunchedEffect(state) {
        when (state) {
            is LoginState.Success -> {
                val token = prefs.getAccessToken().orEmpty()
                val role = decodeRoleFromJwt(token)?.lowercase()
                val goTrainer = (role == "trainer" || role == "admin")

                if (goTrainer) {
                    navController.navigate("trainerRoot") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(NavItem.Home) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is LoginState.Error -> {
                val msg = (state as LoginState.Error).message
                snackbar.showSnackbar(msg)
            }
            else -> Unit
        }
    }

    val loading = state is LoginState.Loading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Witaj w ProTren",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Zaloguj się, aby kontynuować swój plan treningowy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(254) },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Hasło") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (email.isBlank() || password.isBlank()) {
                                        snackbar.showSnackbar("Podaj email i hasło")
                                    } else {
                                        vm.login(email, password)
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Logowanie…")
                            } else {
                                Text("Zaloguj się")
                            }
                        }

                        TextButton(
                            onClick = {
                                forgotEmail = email
                                showForgotDialog = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Nie pamiętasz hasła?")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = {
                    navController.navigate(NavItem.Register) {
                        launchSingleTop = true
                    }
                }) {
                    Text("Nie masz konta? Załóż nowe")
                }
            }

            if (showForgotDialog) {
                AlertDialog(
                    onDismissRequest = { if (!forgotLoading) showForgotDialog = false },
                    title = { Text("Reset hasła") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Podaj adres e-mail, na który wyślemy link do resetu hasła.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedTextField(
                                value = forgotEmail,
                                onValueChange = { forgotEmail = it },
                                label = { Text("Adres e-mail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val mail = forgotEmail.trim()
                                    if (mail.isBlank()) {
                                        snackbar.showSnackbar("Podaj adres e-mail")
                                        return@launch
                                    }
                                    forgotLoading = true
                                    try {
                                        val resp = authApi.forgotPassword(
                                            ForgotPasswordRequest(email = mail)
                                        )
                                        if (resp.isSuccessful) {
                                            val msg = resp.body()?.msg
                                                ?: "Jeśli podany email istnieje, wysłaliśmy link do resetu hasła."
                                            snackbar.showSnackbar(msg)
                                            showForgotDialog = false
                                        } else {
                                            snackbar.showSnackbar("Nie udało się wysłać maila resetującego.")
                                        }
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("Błąd połączenia. Spróbuj ponownie.")
                                    } finally {
                                        forgotLoading = false
                                    }
                                }
                            },
                            enabled = !forgotLoading
                        ) {
                            if (forgotLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Wyślij link")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!forgotLoading) showForgotDialog = false }
                        ) {
                            Text("Anuluj")
                        }
                    }
                )
            }
        }
    }
}

private fun decodeRoleFromJwt(token: String?): String? {
    if (token.isNullOrBlank()) return null
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = String(
            Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        )
        JSONObject(payload).optString("role", null)
    } catch (_: Exception) {
        null
    }
}
