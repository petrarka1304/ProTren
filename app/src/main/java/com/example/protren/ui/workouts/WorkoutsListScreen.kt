@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.workouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import com.example.protren.data.UserPreferences
import com.example.protren.model.WorkoutLog

private const val WORKOUTS_ROUTE = "workouts"
private const val RESULT_KEY = "new_workout_item"

// Stan filtra
private enum class WorkoutFilter { ALL, PLANNED, DONE }

// Model do listy
data class WorkoutListItemUi(
    val id: String,
    val date: String,
    val title: String,
    val volume: String,
    val status: String    // "planned" / "done"
) : Serializable

// Pomocnicza funkcja: dla starych logów bez statusu
private fun inferStatusFromDate(rawDate: String?): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val ld = rawDate?.take(10)?.let { LocalDate.parse(it, formatter) }
        val today = LocalDate.now()
        when {
            ld == null -> "done"
            ld.isAfter(today) -> "planned"
            ld.isBefore(today) -> "done"
            else -> "done"
        }
    } catch (_: Exception) {
        "done"
    }
}

@Composable
fun WorkoutsListScreen(navController: NavController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(ctx) }

    var items by remember { mutableStateOf<List<WorkoutListItemUi>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    var filter by rememberSaveable { mutableStateOf(WorkoutFilter.ALL) }

    suspend fun fetchWorkouts(): List<WorkoutListItemUi> {
        val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
        val api = ApiClient.createWithAuth(
            tokenProvider = { token },
            onUnauthorized = { scope.launch { snackbar.showSnackbar("Sesja wygasła – zaloguj się ponownie.") } }
        ).create(WorkoutApi::class.java)

        val res = withContext(Dispatchers.IO) { api.getWorkoutLogs() }
        return if (res.isSuccessful) {
            res.body().orEmpty().sortedByDescending { it.date ?: "" }.map {
                val ex = it.exercises.orEmpty()
                val totalSets = ex.sumOf { e -> e.sets ?: 0 }
                val totalVol = ex.sumOf { e -> (e.sets ?: 0) * (e.reps ?: 0) * (e.weight ?: 0) }

                val status = it.status ?: inferStatusFromDate(it.date)

                WorkoutListItemUi(
                    id = it.id ?: UUID.randomUUID().toString(),
                    date = (it.date ?: "—").take(10),
                    title = "Trening",
                    volume = "${ex.size} ćw • $totalSets serii • $totalVol kg",
                    status = status
                )
            }
        } else {
            scope.launch { snackbar.showSnackbar("Błąd pobierania: HTTP ${res.code()}") }
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        items = fetchWorkouts()
        loading = false
    }

    // Echo z AddWorkout
    LaunchedEffect(Unit) {
        val handle = try {
            navController.getBackStackEntry(WORKOUTS_ROUTE).savedStateHandle
        } catch (_: Exception) {
            navController.currentBackStackEntry?.savedStateHandle
        } ?: return@LaunchedEffect

        handle.getStateFlow<WorkoutListItemUi?>(RESULT_KEY, null)
            .collectLatest { incoming ->
                if (incoming != null) {
                    if (items.none { it.id == incoming.id }) {
                        items = listOf(incoming) + items
                    }
                    handle[RESULT_KEY] = null
                }
            }
    }

    suspend fun deleteWorkout(id: String) {
        deletingId = id
        try {
            val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
            val api = ApiClient.createWithAuth(tokenProvider = { token }).create(WorkoutApi::class.java)
            val res = withContext(Dispatchers.IO) { api.deleteWorkout(id) }
            if (res.isSuccessful) {
                items = fetchWorkouts()
                snackbar.showSnackbar("Trening usunięty")
            } else {
                snackbar.showSnackbar("Nie udało się usunąć (HTTP ${res.code()})")
            }
        } catch (e: Exception) {
            snackbar.showSnackbar("Błąd sieci: ${e.localizedMessage ?: "nieznany"}")
        } finally {
            deletingId = null
        }
    }

    val filteredItems = when (filter) {
        WorkoutFilter.ALL -> items
        WorkoutFilter.PLANNED -> items.filter { it.status == "planned" }
        WorkoutFilter.DONE -> items.filter { it.status == "done" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Treningi")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("addWorkout") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj trening") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!loading && items.isNotEmpty()) {
                // Pasek filtrów
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filter == WorkoutFilter.ALL,
                        onClick = { filter = WorkoutFilter.ALL },
                        label = { Text("Wszystkie") }
                    )
                    FilterChip(
                        selected = filter == WorkoutFilter.PLANNED,
                        onClick = { filter = WorkoutFilter.PLANNED },
                        label = { Text("Zaplanowane") }
                    )
                    FilterChip(
                        selected = filter == WorkoutFilter.DONE,
                        onClick = { filter = WorkoutFilter.DONE },
                        label = { Text("Wykonane") }
                    )
                }
            }

            when {
                loading -> Box(
                    Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                items.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                                Spacer(Modifier.height(12.dp))
                                Text("Brak zapisanych sesji", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Dodaj pierwszy trening, aby rozpocząć śledzenie postępów.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = { navController.navigate("addWorkout") }) {
                                    Text("Dodaj trening")
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { w ->
                            ElevatedCard(
                                onClick = { navController.navigate("editWorkout/${w.id}") },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.FitnessCenter,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                w.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                w.date,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Znacznik statusu
                                        AssistChip(
                                            onClick = {},
                                            enabled = false,
                                            label = {
                                                Text(
                                                    if (w.status == "planned") "Zaplanowany" else "Wykonany"
                                                )
                                            }
                                        )

                                        Spacer(Modifier.width(8.dp))

                                        IconButton(onClick = {
                                            navController.navigate("editWorkout/${w.id}")
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edytuj")
                                        }
                                        IconButton(
                                            enabled = deletingId != w.id,
                                            onClick = { scope.launch { deleteWorkout(w.id) } }
                                        ) {
                                            if (deletingId == w.id) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        w.volume,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
