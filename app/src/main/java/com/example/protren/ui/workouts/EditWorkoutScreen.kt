@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ── Twoje pakiety ─────────────────────────────────────────────────────────────
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import com.example.protren.network.UpdateWorkoutRequest
import com.example.protren.model.WorkoutLog
import com.example.protren.model.Exercise
import com.example.protren.model.CreateWorkoutRequest
import com.example.protren.data.UserPreferences
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES

@Composable
fun EditWorkoutScreen(
    navController: NavController,
    workoutId: String
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(ctx) }

    // --- STATE ---
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US) }
    var date by remember { mutableStateOf("") }
    val showDatePicker = remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    // UŻYWAMY DraftExerciseUi z AddWorkoutScreen (ta sama paczka)
    val draft = remember { mutableStateListOf<DraftExerciseUi>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // --- LIMITY (ProTren) ---
    val MAX_SETS_CHARS = 2     // np. 99
    val MAX_REPS_CHARS = 3     // np. 999
    val MAX_WEIGHT_CHARS = 4   // np. 9999

    fun digitsOnlyLimited(input: TextFieldValue, maxChars: Int): TextFieldValue {
        val filtered = input.text.filter { it.isDigit() }.take(maxChars)
        // zachowujemy sensowną pozycję kursora
        val newCursor = filtered.length.coerceAtMost(input.selection.end)
        return TextFieldValue(filtered, selection = androidx.compose.ui.text.TextRange(newCursor))
    }

    // --- HELPERS ---
    fun computeVolumePreview(): String {
        val totalSets = draft.sumOf { it.sets }
        val totalVolume = draft.sumOf { it.sets * it.reps * it.weight }
        return "Objętość: $totalVolume kg • serie: $totalSets"
    }

    // --- LOAD WORKOUT BY ID ---
    LaunchedEffect(workoutId) {
        loading = true
        try {
            val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
            val api = ApiClient.createWithAuth(tokenProvider = { token })
                .create(WorkoutApi::class.java)

            val res = withContext(Dispatchers.IO) { api.getWorkout(workoutId) }
            if (res.isSuccessful) {
                val body: WorkoutLog? = res.body()
                date = (body?.date ?: "").take(10)
                draft.clear()
                body?.exercises.orEmpty().forEach { e ->
                    draft.add(
                        DraftExerciseUi(
                            id = UUID.randomUUID().toString(),               // ← lokalne ID tylko do UI
                            name = e.name ?: "Ćwiczenie",
                            sets = e.sets ?: 0,
                            reps = e.reps ?: 0,
                            weight = e.weight ?: 0
                        )
                    )
                }
            } else {
                snackbar.showSnackbar("Błąd pobierania: HTTP ${res.code()}")
            }
        } catch (e: Exception) {
            snackbar.showSnackbar("Błąd sieci: ${e.localizedMessage ?: "nieznany"}")
        } finally {
            loading = false
        }
    }

    // --- ODBIÓR NOWYCH ĆWICZEŃ Z PICKERA (jak w Add) ---
    LaunchedEffect(Unit) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<ArrayList<String>?>(EXERCISE_PICKER_RESULT_IDS, null)
            .collect { ids ->
                val names = handle.get<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
                if (!ids.isNullOrEmpty()) {
                    ids.forEachIndexed { idx, idStr ->
                        val n = names?.getOrNull(idx) ?: "Ćwiczenie"
                        if (draft.none { it.id == idStr }) {
                            draft.add(
                                DraftExerciseUi(
                                    id = idStr,
                                    name = n
                                )
                            )
                        }
                    }
                    handle.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_IDS)
                    handle.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
                }
            }
    }

    // --- SAVE (PUT) ---
    fun saveEdit() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
                val api = ApiClient.createWithAuth(tokenProvider = { token })
                    .create(WorkoutApi::class.java)

                val body = UpdateWorkoutRequest(
                    date = date.ifBlank { null },
                    exercises = draft.map {
                        Exercise(                                  // ← BEZ parametru id
                            name = it.name,
                            sets = it.sets,
                            reps = it.reps,
                            weight = it.weight,
                            notes = null
                        )
                    },
                    trainingPlanId = null
                )

                val res = withContext(Dispatchers.IO) { api.updateWorkout(workoutId, body) }
                if (res.isSuccessful) {
                    navController.navigateUp()
                } else {
                    snackbar.showSnackbar("Błąd zapisu: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Błąd sieci: ${e.localizedMessage ?: "nieznany"}")
            } finally {
                saving = false
            }
        }
    }

    // --- SAVE AS NEW (POST) ---
    fun saveAsNew() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
                val api = ApiClient.createWithAuth(tokenProvider = { token })
                    .create(WorkoutApi::class.java)

                val body = CreateWorkoutRequest(
                    date = date.ifBlank { null },
                    exercises = draft.map {
                        Exercise(                                  // ← BEZ parametru id
                            name = it.name,
                            sets = it.sets,
                            reps = it.reps,
                            weight = it.weight,
                            notes = null
                        )
                    },
                    trainingPlanId = null
                )

                val res = withContext(Dispatchers.IO) { api.createWorkout(body) }
                if (res.isSuccessful) {
                    navController.navigateUp()
                } else {
                    snackbar.showSnackbar("Błąd zapisu: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Błąd sieci: ${e.localizedMessage ?: "nieznany"}")
            } finally {
                saving = false
            }
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edytuj trening") },
                actions = {
                    Row {
                        TextButton(onClick = { saveEdit() }, enabled = !saving) { Text("ZAPISZ") }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("exercisePicker") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj ćwiczenia") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Data
                item {
                    val interaction = remember { MutableInteractionSource() }
                    OutlinedTextField(
                        value = date,
                        onValueChange = { /* readOnly */ },
                        label = { Text("Data (YYYY-MM-DD), opcjonalnie") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker.value = true }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Wybierz datę")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = interaction, indication = null) {
                                showDatePicker.value = true
                            }
                    )
                }

                // Podsumowanie
                item {
                    AssistChip(onClick = {}, label = { Text(computeVolumePreview()) }, enabled = false)
                }

                // Ćwiczenia
                if (draft.isEmpty()) {
                    item {
                        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Brak ćwiczeń",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Dodaj ćwiczenia przyciskiem poniżej.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(draft, key = { it.id }) { ex ->
                        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ex.name, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editingIndex = draft.indexOf(ex) }) { Text("Serie…") }
                                    TextButton(onClick = { draft.remove(ex) }) { Text("Usuń") }
                                }
                                Text(
                                    "${ex.sets}×${ex.reps} • ${ex.weight} kg",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // DatePicker
    if (showDatePicker.value) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        date = localDate.format(dateFormatter)
                    }
                    showDatePicker.value = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker.value = false }) { Text("Anuluj") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    // Dialog „Serie…”
    if (editingIndex != null) {
        val idx = editingIndex!!
        val ex = draft[idx]
        var sets by remember(ex) { mutableStateOf(TextFieldValue(ex.sets.toString())) }
        var reps by remember(ex) { mutableStateOf(TextFieldValue(ex.reps.toString())) }
        var weight by remember(ex) { mutableStateOf(TextFieldValue(ex.weight.toString())) }

        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Ustaw serie dla: ${ex.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { incoming ->
                            sets = digitsOnlyLimited(incoming, MAX_SETS_CHARS)
                        },
                        label = { Text("Serie") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Maks. $MAX_SETS_CHARS cyfry") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { incoming ->
                            reps = digitsOnlyLimited(incoming, MAX_REPS_CHARS)
                        },
                        label = { Text("Powtórzenia") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Maks. $MAX_REPS_CHARS cyfry") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { incoming ->
                            weight = digitsOnlyLimited(incoming, MAX_WEIGHT_CHARS)
                        },
                        label = { Text("Ciężar [kg]") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Maks. $MAX_WEIGHT_CHARS cyfry") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = sets.text.toIntOrNull() ?: ex.sets
                    val r = reps.text.toIntOrNull() ?: ex.reps
                    val w = weight.text.toIntOrNull() ?: ex.weight
                    draft[idx] = ex.copy(sets = s, reps = r, weight = w)
                    editingIndex = null
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text("Anuluj") } }
        )
    }
}
