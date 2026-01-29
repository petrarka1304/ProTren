@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.main

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.LocalThemeController
import com.example.protren.viewmodel.AccountUIState
import com.example.protren.viewmodel.AccountViewModel
import com.example.protren.viewmodel.AccountViewModelFactory
import androidx.compose.ui.Alignment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.navigation.NavItem
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainerApi
import com.example.protren.network.TrainerSettingsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    navController: NavController
) {
    val app = (LocalContext.current.applicationContext as Application)

    val vm: AccountViewModel = viewModel(factory = AccountViewModelFactory(app))
    val uiState by vm.state.collectAsState()

    val snack = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }

    val themeController = LocalThemeController.current
    var isDark by remember { mutableStateOf(themeController.isDark) }

    var isTrainer by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is AccountUIState.Loaded -> {
                firstName = s.me.firstName.orEmpty()
                lastName = s.me.lastName.orEmpty()
                email = s.me.email.orEmpty()
                isTrainer = s.me.role?.equals("trainer", ignoreCase = true) == true
            }
            is AccountUIState.Saved -> {
                s.me?.let {
                    firstName = it.firstName.orEmpty()
                    lastName = it.lastName.orEmpty()
                    email = it.email.orEmpty()
                    isTrainer = it.role?.equals("trainer", ignoreCase = true) == true
                }
                s.msg?.let { snack.showSnackbar(it) }
            }
            is AccountUIState.Deleted -> {
                scope.launch {
                    snack.showSnackbar(s.msg ?: "Konto zostało usunięte.")
                }
                navController.navigate(NavItem.Login) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            is AccountUIState.Error -> {
                snack.showSnackbar(s.message)
            }
            AccountUIState.Loading -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz"
                        )
                    }
                },
                title = { Text("Ustawienia konta") }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dane podstawowe", fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Imię") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nazwisko") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("E-mail") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { vm.saveName(firstName, lastName) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState !is AccountUIState.Loading
                ) {
                    Text("Zapisz dane")
                }
            }

            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Motyw aplikacji", fontWeight = FontWeight.SemiBold)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Ciemny motyw")
                    Switch(
                        checked = isDark,
                        onCheckedChange = { checked ->
                            isDark = checked
                            themeController.setDark(checked)
                        }
                    )
                }
                
            }

            if (isTrainer) {
                Divider()

                val trainerVm: TrainerSettingsViewModel =
                    viewModel(factory = TrainerSettingsViewModelFactory(app))
                val trainerState by trainerVm.state.collectAsState()

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ustawienia trenera", fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = trainerState.maxTrainees,
                        onValueChange = { trainerVm.onMaxTraineesChange(it) },
                        label = { Text("Limit podopiecznych") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (trainerState.error != null) {
                        Text(
                            trainerState.error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            trainerVm.save { msg ->
                                scope.launch {
                                    snack.showSnackbar(msg)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !trainerState.loading
                    ) {
                        Text(
                            if (trainerState.loading)
                                "Zapisywanie…"
                            else
                                "Zapisz limit"
                        )
                    }
                }
            }

            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Konto", fontWeight = FontWeight.SemiBold)

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState !is AccountUIState.Loading
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Usuń konto")
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Usunąć konto?") },
                text = {
                    Text(
                        "Tej operacji nie można cofnąć. Wszystkie dane zostaną skasowane."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            vm.deleteAccount()
                        }
                    ) {
                        Text("Usuń", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Anuluj")
                    }
                }
            )
        }
    }
}


data class TrainerSettingsUiState(
    val maxTrainees: String = "10",
    val loading: Boolean = false,
    val error: String? = null
)

class TrainerSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)

    private fun api(): TrainerApi {
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { access, refresh ->
                prefs.setTokens(access, refresh)
            },
            onUnauthorized = {
                prefs.clearAll()
            }
        )
        return retrofit.create(TrainerApi::class.java)
    }

    private val _state = MutableStateFlow(TrainerSettingsUiState())
    val state: StateFlow<TrainerSettingsUiState> = _state

    init {
        loadCurrentLimit()
    }

    private fun loadCurrentLimit() {
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api().getMyOffer()
                }

                if (res.isSuccessful) {
                    val body = res.body()
                    val max = body?.maxTrainees
                    if (max != null && max > 0) {
                        _state.value = _state.value.copy(
                            maxTrainees = max.toString(),
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun onMaxTraineesChange(value: String) {
        _state.value = _state.value.copy(
            maxTrainees = value,
            error = null
        )
    }

    fun save(onDone: (String) -> Unit = {}) {
        val parsed = _state.value.maxTrainees.toIntOrNull()
        if (parsed == null || parsed < 1) {
            _state.value = _state.value.copy(error = "Podaj liczbe > 0")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val res = withContext(Dispatchers.IO) {
                    api().updateTrainerSettings(TrainerSettingsRequest(parsed))
                }

                if (res.isSuccessful) {
                    val newVal = res.body()?.maxTrainees ?: parsed
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        maxTrainees = newVal.toString()
                    )
                    onDone("Zapisano limit: $newVal")
                } else {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "Błąd HTTP ${res.code()}"
                    )
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.localizedMessage
                )
            }
        }
    }
}

class TrainerSettingsViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrainerSettingsViewModel::class.java)) {
            return TrainerSettingsViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
