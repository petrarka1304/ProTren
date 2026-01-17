package com.example.protren

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    token: String?,
    padding: PaddingValues,
    onSubmit: (newPassword: String, onDone: () -> Unit) -> Unit,
    onFinished: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val canSubmit = !loading &&
            password.length >= 6 &&
            password == password2 &&
            !token.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ustaw nowe hasło",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        if (token.isNullOrBlank()) {
            Text(
                text = "Link resetujący jest nieprawidłowy lub wygasł.",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinished) {
                Text("Zamknij")
            }
            return
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Nowe hasło") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password2,
            onValueChange = { password2 = it },
            label = { Text("Powtórz hasło") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                loading = true
                onSubmit(password) {
                    loading = false
                    onFinished()
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Zapisuję…")
            } else {
                Text("Zmień hasło")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onFinished,
            enabled = !loading
        ) {
            Text("Anuluj")
        }
    }
}
