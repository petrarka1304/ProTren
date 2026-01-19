@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.register

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.viewmodel.RegisterState
import com.example.protren.viewmodel.RegisterViewModel
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val app = LocalContext.current.applicationContext as Application
    val prefs = remember { UserPreferences(app) }
    val vm: RegisterViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RegisterViewModel(prefs) as T
    })

    val state by vm.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }

    var isTrainer by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is RegisterState.Error -> {
                val msg = (state as RegisterState.Error).message
                snackbar.showSnackbar(msg)
            }
            RegisterState.Success -> {
                snackbar.showSnackbar("Konto utworzone Zaloguj się.")
                navController.navigate("login") {
                    popUpTo("register") { inclusive = true }
                    launchSingleTop = true
                }
            }
            else -> Unit
        }
    }

    val loading = state is RegisterState.Loading

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Rejestracja") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Załóż konto", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Dołącz i zacznij śledzić swoje treningi.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(254) },
                                label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Hasło (min. 6 znaków)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pass2,
                        onValueChange = { pass2 = it },
                        label = { Text("Powtórz hasło") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Rodzaj konta", style = MaterialTheme.typography.titleMedium)

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isTrainer) "Konto trenerskie"
                                else "Konto użytkownika"
                            )
                            Text(
                                if (isTrainer)
                                    "Dostęp do panelu trenera i podopiecznych."
                                else
                                    "Standardowe konto do własnych treningów.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTrainer,
                            onCheckedChange = { isTrainer = it }
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                when {
                                    email.isBlank() || pass.isBlank() ->
                                        snackbar.showSnackbar("Podaj email i hasło")
                                    pass.length < 6 ->
                                        snackbar.showSnackbar("Hasło min. 6 znaków")
                                    pass != pass2 ->
                                        snackbar.showSnackbar("Hasła nie są identyczne")
                                    else ->
                                        vm.register(email, pass, isTrainer)
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Zarejestruj się")
                    }

                    TextButton(
                        onClick = { navController.navigate("login") { launchSingleTop = true } },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Masz już konto? Zaloguj się")
                    }
                }
            }
        }
    }
}
