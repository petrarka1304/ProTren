@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.main

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.protren.navigation.AppNavGraph
import com.example.protren.viewmodel.AppRole
import com.example.protren.viewmodel.RoleViewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // VM roli (pobiera z JWT + /users/me)
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val roleVm: RoleViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RoleViewModel(app) as T
    })
    val role by roleVm.role.collectAsState()
    val loading by roleVm.loading.collectAsState()
    val error by roleVm.error.collectAsState()

    LaunchedEffect(Unit) { roleVm.load() }

    // Jeśli wystąpi błąd potwierdzenia roli z serwera, pokaż komunikat,
    // ale NIE zmieniaj wyglądu na „użytkownika” – jedziemy na roli z JWT.
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(
                message = "Nie udało się potwierdzić roli z serwera — używam roli z tokena."
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        when {
            loading || role == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> {
                val isTrainer = (role == AppRole.TRAINER)
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    AppNavGraph(
                        navController = navController,
                        isTrainer = isTrainer
                    )
                }
            }
        }
    }
}
