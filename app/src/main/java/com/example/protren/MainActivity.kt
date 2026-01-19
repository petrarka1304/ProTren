package com.example.protren

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.protren.network.ErrorBus
import com.example.protren.auth.AuthBus
import com.example.protren.auth.AuthEvent
import com.example.protren.data.UserPreferences
import com.example.protren.ui.main.MainScreen
import com.example.protren.ui.theme.ProTrenTheme
import kotlinx.coroutines.flow.collectLatest

data class ThemeController(
    val isDark: Boolean,
    val setDark: (Boolean) -> Unit
)

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProTrenApp() }
    }
}

@Composable
private fun ProTrenApp() {
    var isDarkTheme by remember { mutableStateOf(true) }

    CompositionLocalProvider(
        LocalThemeController provides ThemeController(
            isDark = isDarkTheme,
            setDark = { isDarkTheme = it }
        )
    ) {
        ProTrenTheme(darkTheme = isDarkTheme) {
            val snackbar = remember { SnackbarHostState() }

            var showLoggedOut by remember { mutableStateOf(false) }
            var loggedOutMsg by remember { mutableStateOf("Sesja wygasła. Zaloguj się ponownie.") }

            val context = LocalContext.current
            val prefs = remember(context) { UserPreferences(context) }
            LaunchedEffect(Unit) {
                AuthBus.events.collectLatest { ev ->
                    if (ev is AuthEvent.LoggedOut) {
                        prefs.clearAll()

                        loggedOutMsg = ev.reason.ifBlank {
                            "Sesja wygasła. Zaloguj się ponownie."
                        }
                        showLoggedOut = true
                    }
                }
            }
            LaunchedEffect(Unit) {
                ErrorBus.messages.collectLatest { msg ->
                    snackbar.showSnackbar(msg)
                }
            }


            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) }
            ) { innerPadding ->

                MainScreen(modifier = Modifier.padding(innerPadding))

                if (showLoggedOut) {
                    AlertDialog(
                        onDismissRequest = { /* wymuszamy akcję */ },
                        title = { Text(text = "Sesja wygasła") },
                        text = { Text(text = loggedOutMsg) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showLoggedOut = false

                                    prefs.clearAll()

                                    val launchIntent = context.packageManager
                                        .getLaunchIntentForPackage(context.packageName)
                                        ?.apply {
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            )
                                        }

                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        val fallback = Intent(context, MainActivity::class.java).apply {
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            )
                                        }
                                        context.startActivity(fallback)
                                    }
                                }
                            ) {
                                Text(text = "Zaloguj się ponownie")
                            }
                        }
                    )
                }
            }
        }
    }
}
