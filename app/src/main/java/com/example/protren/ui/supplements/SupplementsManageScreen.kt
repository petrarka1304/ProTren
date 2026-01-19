@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.supplements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.navigation.NavItem
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
import kotlinx.coroutines.launch

@Composable
fun SupplementsManageScreen(
    navController: NavController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var supplements by remember { mutableStateOf<List<Supplement>>(emptyList()) }
    val currentBackStack = navController.currentBackStackEntry
    val savedStateHandle = currentBackStack?.savedStateHandle

    fun buildApi(): SupplementApi? {
        val token = prefs.getAccessToken() ?: return null
        val client = ApiClient.createWithAuth(
            tokenProvider = { token },
            onUnauthorized = { /* TODO: wyloguj jeśli trzeba */ }
        )
        return client.create(SupplementApi::class.java)
    }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val api = buildApi() ?: run {
                    error = "Brak tokena – zaloguj się ponownie."
                    return@launch
                }
                val resp = api.getAll()
                if (resp.isSuccessful) {
                    supplements = resp.body().orEmpty()
                } else {
                    error = "Błąd pobierania: ${resp.code()}"
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Błąd połączenia"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(savedStateHandle) {
        savedStateHandle
            ?.getStateFlow("supplements_changed", false)
            ?.collect { changed ->
                if (changed) {
                    reload()
                    savedStateHandle["supplements_changed"] = false
                }
            }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Wszystkie suplementy") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = { reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                    IconButton(onClick = { navController.navigate(NavItem.SupplementEditor) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Dodaj suplement")
                    }
                },
                scrollBehavior = scroll
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { reload() }) {
                            Text("Spróbuj ponownie")
                        }
                    }
                }
            }

            supplements.isEmpty() -> {
                EmptySupplementsState(
                    onAdd = { navController.navigate(NavItem.SupplementEditor) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Twoje suplementy",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(supplements, key = { it._id.orEmpty() }) { sup ->
                        SupplementManageItem(
                            supplement = sup,
                            onEdit = { id ->
                                navController.navigate("${NavItem.SupplementEditor}?id=$id")
                            },
                            onDelete = { id ->
                                scope.launch {
                                    try {
                                        val api = buildApi() ?: return@launch
                                        val resp = api.delete(id)
                                        if (resp.isSuccessful || resp.code() == 204) {
                                            navController.currentBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("supplements_changed", true)
                                            reload()
                                            snackbar.showSnackbar("Usunięto suplement")
                                        } else {
                                            snackbar.showSnackbar("Nie udało się usunąć: ${resp.code()}")
                                        }
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(e.localizedMessage ?: "Błąd usuwania")
                                    }
                                }
                            }
                        )
                    }

                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptySupplementsState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Medication,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Text("Brak suplementów", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Dodaj pierwszy suplement, aby wyświetlać przypomnienia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAdd) { Text("Dodaj suplement") }
            }
        }
    }
}

@Composable
private fun SupplementManageItem(
    supplement: Supplement,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var askDelete by remember { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        supplement.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val dosage = supplement.dosage.orEmpty()
                    val times = (supplement.times ?: emptyList()).joinToString(", ") {
                        when (it) {
                            "morning" -> "rano"
                            "midday" -> "południe"
                            "evening" -> "wieczór"
                            "night" -> "noc"
                            else -> it
                        }
                    }
                    val days = (supplement.daysOfWeek ?: emptyList()).joinToString(", ") {
                        when (it) {
                            1 -> "Pn"
                            2 -> "Wt"
                            3 -> "Śr"
                            4 -> "Cz"
                            5 -> "Pt"
                            6 -> "Sb"
                            0 -> "Nd"
                            else -> it.toString()
                        }
                    }

                    val meta = listOfNotNull(
                        if (dosage.isNotBlank()) dosage else null,
                        if (times.isNotBlank()) times else null,
                        if (days.isNotBlank()) days else null
                    ).joinToString("  •  ")

                    if (meta.isNotBlank()) {
                        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = { onEdit(supplement._id.orEmpty()) }) {
                    Icon(Icons.Filled.Medication, contentDescription = "Edytuj")
                }
                IconButton(onClick = { askDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                }
            }

            if (!supplement.notes.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    supplement.notes.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Usunąć suplement?") },
            text = { Text("Tej operacji nie będzie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    onDelete(supplement._id.orEmpty())
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("Anuluj") }
            }
        )
    }
}
