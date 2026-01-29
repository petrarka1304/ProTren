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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.Exercise
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import com.example.protren.ui.exercises.EXERCISE_PICKER_PRESELECTED_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.text.TextRange
import com.example.protren.network.UpdateWorkoutRequest

@Composable
fun EditWorkoutScreen(
    navController: NavController,
    workoutId: String
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    var title by remember { mutableStateOf("") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US) }
    var date by remember { mutableStateOf("") }
    val showDatePicker = remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    val draft = remember { mutableStateListOf<DraftExerciseUi>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val MAX_SETS_CHARS = 2
    val MAX_REPS_CHARS = 3
    val MAX_WEIGHT_CHARS = 4

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val handle = navController.currentBackStackEntry?.savedStateHandle
                val ids = handle?.get<ArrayList<String>>(EXERCISE_PICKER_RESULT_IDS)
                val names = handle?.get<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)

                if (!ids.isNullOrEmpty()) {
                    ids.forEachIndexed { idx, _ ->
                        val pickedName = names?.getOrNull(idx) ?: ""

                        if (pickedName.isNotBlank() && draft.none { it.name.equals(pickedName, ignoreCase = true) }) {
                            draft.add(
                                DraftExerciseUi(
                                    id = UUID.randomUUID().toString(),
                                    name = pickedName,
                                    sets = 3,
                                    reps = 10,
                                    weight = 0
                                )
                            )
                        }
                    }
                    handle.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_IDS)
                    handle.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(workoutId) {
        loading = true
        try {
            val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
            val api = ApiClient.createWithAuth(tokenProvider = { token }).create(WorkoutApi::class.java)

            val res = withContext(Dispatchers.IO) { api.getWorkout(workoutId) }
            if (res.isSuccessful) {
                val body = res.body()
                date = (body?.date ?: "").take(10)
                title = body?.title ?: ""
                draft.clear()

                body?.exercises.orEmpty().forEach { e ->
                    val exerciseName = e.name?.trim() ?: ""
                    if (exerciseName.isNotBlank()) {
                        draft.add(
                            DraftExerciseUi(
                                id = UUID.randomUUID().toString(),
                                name = exerciseName,
                                sets = e.sets ?: 0,
                                reps = e.reps ?: 0,
                                weight = e.weight ?: 0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            snackbar.showSnackbar("Błąd ładowania")
        } finally {
            loading = false
        }
    }

    fun digitsOnlyLimited(input: TextFieldValue, maxChars: Int): TextFieldValue {
        val filtered = input.text.filter { it.isDigit() }.take(maxChars)
        val newCursor = filtered.length.coerceAtMost(input.selection.end)
        return TextFieldValue(filtered, selection = TextRange(newCursor))
    }

    fun computeVolumePreview(): String {
        val totalSets = draft.sumOf { it.sets }
        val totalVolume = draft.sumOf { it.sets * it.reps * it.weight }
        return "Objętość: $totalVolume kg • serie: $totalSets"
    }

    fun saveEdit() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(WorkoutApi::class.java)

                val body = UpdateWorkoutRequest(
                    date = date.ifBlank { null },
                    title = title,
                    exercises = draft.map {
                        Exercise(name = it.name, sets = it.sets, reps = it.reps, weight = it.weight, notes = null)
                    },
                    trainingPlanId = null
                )

                val res = withContext(Dispatchers.IO) { api.updateWorkout(workoutId, body) }
                if (res.isSuccessful) navController.navigateUp()
                else {
                    snackbar.showSnackbar("Błąd zapisu")
                    saving = false
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Błąd połączenia")
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edytuj trening") },
                actions = {
                    TextButton(onClick = { saveEdit() }, enabled = !saving) {
                        Text(if (saving) "ZAPIS..." else "ZAPISZ")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        EXERCISE_PICKER_PRESELECTED_IDS,
                        ArrayList(draft.map { it.name })
                    )
                    navController.navigate("exercisePicker")
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Dodaj ćwiczenia") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nazwa treningu") },
                        placeholder = { Text("np. Góra, Dół, FBW...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { },
                        label = { Text("Data (YYYY-MM-DD)") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker.value = true }) {
                                Icon(Icons.Filled.CalendarMonth, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker.value = true }
                    )
                }

                item {
                    AssistChip(onClick = {}, label = { Text(computeVolumePreview()) }, enabled = false)
                }

                if (draft.isEmpty()) {
                    item {
                        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Brak ćwiczeń", fontWeight = FontWeight.Bold)
                                Text("Dodaj ćwiczenia przyciskiem poniżej.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(draft, key = { it.id }) { ex ->
                        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ex.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { editingIndex = draft.indexOf(ex) }) { Text("Serie") }
                                    TextButton(onClick = { draft.remove(ex) }) { Text("Usuń") }
                                }
                                Text("${ex.sets}×${ex.reps} • ${ex.weight} kg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

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

    if (editingIndex != null) {
        val idx = editingIndex!!
        val ex = draft.getOrNull(idx) ?: return@EditWorkoutScreen
        var sets by remember(ex) { mutableStateOf(TextFieldValue(ex.sets.toString())) }
        var reps by remember(ex) { mutableStateOf(TextFieldValue(ex.reps.toString())) }
        var weight by remember(ex) { mutableStateOf(TextFieldValue(ex.weight.toString())) }

        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Edytuj: ${ex.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = digitsOnlyLimited(it, MAX_SETS_CHARS) },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = digitsOnlyLimited(it, MAX_REPS_CHARS) },
                        label = { Text("Powtórzenia") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = digitsOnlyLimited(it, MAX_WEIGHT_CHARS) },
                        label = { Text("Ciężar [kg]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = sets.text.toIntOrNull() ?: ex.sets
                    val r = reps.text.toIntOrNull() ?: ex.reps
                    val w = weight.text.toIntOrNull() ?: ex.weight
                    draft[idx] = ex.copy(sets = s.coerceAtLeast(1), reps = r.coerceAtLeast(1), weight = w.coerceAtLeast(0))
                    editingIndex = null
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text("Anuluj") } }
        )
    }
}