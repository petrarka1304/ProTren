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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES
import com.example.protren.ui.exercises.EXERCISE_PICKER_PRESELECTED_IDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import com.example.protren.model.CreateWorkoutRequest
import com.example.protren.model.Exercise
import com.example.protren.model.WorkoutLog
import com.example.protren.data.UserPreferences

private const val WORKOUTS_ROUTE = "workouts"
private const val RESULT_KEY = "new_workout_item"

data class DraftExerciseUi(
    val id: String,
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Int = 0
)

private enum class DateRelation { PAST, TODAY, FUTURE }

@Composable
fun AddWorkoutScreen(navController: NavController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }

    var title by rememberSaveable { mutableStateOf("") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US) }
    var date by rememberSaveable { mutableStateOf("") }
    val showDatePicker = rememberSaveable { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    val draft = remember { mutableStateListOf<DraftExerciseUi>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    var isPlannedToday by rememberSaveable { mutableStateOf(true) }

    fun resolveDateAndRelation(): Pair<LocalDate, DateRelation> {
        val chosen = runCatching {
            if (date.isBlank()) today else LocalDate.parse(date, dateFormatter)
        }.getOrElse { today }

        val relation = when {
            chosen.isBefore(today) -> DateRelation.PAST
            chosen.isAfter(today) -> DateRelation.FUTURE
            else -> DateRelation.TODAY
        }
        return chosen to relation
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val h = navController.currentBackStackEntry?.savedStateHandle
                val ids: ArrayList<String>?   = h?.get(EXERCISE_PICKER_RESULT_IDS)
                val names: ArrayList<String>? = h?.get(EXERCISE_PICKER_RESULT_NAMES)
                if (!ids.isNullOrEmpty()) {
                    val before = draft.size
                    ids.forEachIndexed { idx, id ->
                        val n = names?.getOrNull(idx) ?: "Ćwiczenie"
                        if (draft.none { it.id == id }) {
                            draft.add(DraftExerciseUi(id = id, name = n))
                        }
                    }
                    val addedCount = draft.size - before
                    if (addedCount > 0) {
                        scope.launch {
                            val msg = if (addedCount == 1)
                                "Dodano 1 ćwiczenie do treningu"
                            else
                                "Dodano $addedCount ćwiczenia do treningu"
                            snackbar.showSnackbar(msg)
                        }
                    }
                    h?.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_IDS)
                    h?.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isValid(): Boolean = title.isNotBlank() && draft.isNotEmpty()

    fun computeVolumePreview(): String {
        val exCount = draft.size
        val totalSets = draft.sumOf { it.sets }
        val totalVolume = draft.sumOf { it.sets * it.reps * it.weight }
        return "$exCount ćw • $totalSets serii • $totalVolume kg"
    }

    fun saveAndReturn() {
        if (!isValid()) {
            scope.launch {
                snackbar.showSnackbar(
                    when {
                        title.isBlank() -> "Podaj nazwę treningu."
                        draft.isEmpty() -> "Dodaj przynajmniej jedno ćwiczenie."
                        else -> "Uzupełnij dane."
                    }
                )
            }
            return
        }
        if (isSaving) return
        isSaving = true

        val (effectiveDate, relation) = resolveDateAndRelation()
        val effectiveDateStr = effectiveDate.format(dateFormatter)

        val status = when (relation) {
            DateRelation.PAST -> "done"
            DateRelation.FUTURE -> "planned"
            DateRelation.TODAY ->
                if (isPlannedToday) "planned" else "done"
        }

        val body = CreateWorkoutRequest(
            date = effectiveDateStr,
            status = status,
            exercises = draft.map {
                Exercise(
                    name = it.name,
                    sets = it.sets,
                    reps = it.reps,
                    weight = it.weight,
                    notes = null
                )
            },
            trainingPlanId = null
        )

        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { token },
                    onUnauthorized = {
                        scope.launch {
                            snackbar.showSnackbar("Sesja wygasła. Zaloguj się ponownie.")
                        }
                    }
                )
                val api = retrofit.create(WorkoutApi::class.java)

                val res = withContext(Dispatchers.IO) { api.createWorkout(body) }
                if (res.isSuccessful) {
                    val created: WorkoutLog? = res.body()

                    val handle = runCatching {
                        navController.getBackStackEntry(WORKOUTS_ROUTE).savedStateHandle
                    }.getOrNull() ?: navController.previousBackStackEntry?.savedStateHandle

                    val echo = WorkoutListItemUi(
                        id = created?.id ?: System.currentTimeMillis().toString(),
                        date = (created?.date ?: effectiveDateStr).take(10),
                        title = title.trim(),
                        volume = computeVolumePreview(),
                        status = status
                    )
                    handle?.set(RESULT_KEY, echo)

                    navController.navigateUp()
                } else {
                    snackbar.showSnackbar("Błąd zapisu: ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Błąd sieci: ${e.localizedMessage ?: "nieznany"}")
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodaj trening") },
                actions = {
                    TextButton(
                        onClick = { saveAndReturn() },
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "ZAPIS…" else "ZAPISZ")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val handle = navController.currentBackStackEntry?.savedStateHandle
                    handle?.set(
                        EXERCISE_PICKER_PRESELECTED_IDS,
                        java.util.ArrayList(draft.map { it.id })
                    )
                    navController.navigate("exercisePicker")
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj ćwiczenia") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa treningu *") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val interaction = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = date,
                    onValueChange = { /* readOnly */ },
                    label = { Text("Data (YYYY-MM-DD)") },
                    readOnly = true,
                    enabled = !isSaving,
                    trailingIcon = {
                        IconButton(onClick = { if (!isSaving) showDatePicker.value = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Wybierz datę")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) {
                            if (!isSaving) showDatePicker.value = true
                        }
                )
            }

            item {
                val (_, relation) = resolveDateAndRelation()

                when (relation) {
                    DateRelation.TODAY -> {
                        Text(
                            "Dzisiejszy trening:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isPlannedToday,
                                onClick = { isPlannedToday = true },
                                label = { Text("Tylko planuję") }
                            )
                            FilterChip(
                                selected = !isPlannedToday,
                                onClick = { isPlannedToday = false },
                                label = { Text("Już wykonałem") }
                            )
                        }
                    }
                    DateRelation.PAST -> {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Ten trening będzie zapisany jako WYKONANY (data wstecz).") }
                        )
                    }
                    DateRelation.FUTURE -> {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Ten trening będzie zapisany jako ZAPLANOWANY (data w przyszłości).") }
                        )
                    }
                }
            }

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
                                "Dodaj przynajmniej jedno ćwiczenie, aby zapisać trening.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val handle = navController.currentBackStackEntry?.savedStateHandle
                                    handle?.set(
                                        EXERCISE_PICKER_PRESELECTED_IDS,
                                        java.util.ArrayList(draft.map { it.id })
                                    )
                                    navController.navigate("exercisePicker")
                                }
                            ) {
                                Text("Wybierz ćwiczenia")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("Ćwiczenia", style = MaterialTheme.typography.titleMedium)
                }
                items(draft, key = { it.id }) { ex ->
                    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ex.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        if (!isSaving) {
                                            editingIndex = draft.indexOf(ex)
                                        }
                                    }
                                ) {
                                    Text("Serie…")
                                }
                                TextButton(
                                    onClick = {
                                        if (!isSaving) draft.remove(ex)
                                    }
                                ) {
                                    Text("Usuń")
                                }
                            }
                            Text(
                                "${ex.sets}×${ex.reps} • ${ex.weight} kg",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    AssistChip(
                        onClick = {},
                        label = { Text("Podsumowanie: ${computeVolumePreview()}") },
                        enabled = false
                    )
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
                        val localDate = Instant
                            .ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        date = localDate.format(dateFormatter)
                    }
                    showDatePicker.value = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = false }) {
                    Text("Anuluj")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    editingIndex?.let { idx ->
        if (idx in draft.indices) {
            val ex = draft[idx]
            var setsText by remember(ex) { mutableStateOf(TextFieldValue(ex.sets.toString())) }
            var repsText by remember(ex) { mutableStateOf(TextFieldValue(ex.reps.toString())) }
            var weightText by remember(ex) { mutableStateOf(TextFieldValue(ex.weight.toString())) }

            AlertDialog(
                onDismissRequest = { editingIndex = null },
                title = { Text("Edytuj serie dla: ${ex.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = setsText,
                            onValueChange = { setsText = it },
                            label = { Text("Serie") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it },
                            label = { Text("Powtórzenia") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("Ciężar (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newSets = setsText.text.toIntOrNull() ?: ex.sets
                        val newReps = repsText.text.toIntOrNull() ?: ex.reps
                        val newWeight = weightText.text.toIntOrNull() ?: ex.weight
                        draft[idx] = ex.copy(
                            sets = newSets.coerceAtLeast(1),
                            reps = newReps.coerceAtLeast(1),
                            weight = newWeight.coerceAtLeast(0)
                        )
                        editingIndex = null
                    }) {
                        Text("Zapisz")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingIndex = null }) {
                        Text("Anuluj")
                    }
                }
            )
        } else {
            editingIndex = null
        }
    }
}
