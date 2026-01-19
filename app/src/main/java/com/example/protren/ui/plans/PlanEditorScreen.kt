@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private const val MAX_PLAN_NAME = 60
private const val MAX_DAY_TITLE = 40
private const val MAX_EXERCISE_NAME = 80

private const val MIN_SETS = 1
private const val MAX_SETS = 99
private const val MIN_REPS = 1
private const val MAX_REPS = 999

private fun clampInt(value: Int, min: Int, max: Int): Int = when {
    value < min -> min
    value > max -> max
    else -> value
}

private fun String.onlyDigits(maxLen: Int): String =
    this.filter(Char::isDigit).take(maxLen)

private fun String.sanitizeText(maxLen: Int): String =
    this.trim().replace(Regex("\\s+"), " ").take(maxLen)


private data class ExerciseUi(
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10
)

private data class DayUi(
    val title: String,
    val exercises: MutableList<ExerciseUi> = mutableListOf()
)


private const val EX_RESULT_IDS = "EXERCISE_PICKER_RESULT_IDS"
private const val EX_RESULT_NAMES = "EXERCISE_PICKER_RESULT_NAMES"

// ────────────────────────────────────────────────────────────────────────────────

@Composable
fun PlanEditorScreen(
    navController: NavController,
    planId: String
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    val days = remember { mutableStateListOf<DayUi>() }

    var pendingAddForDayIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(planId) {
        loading = true
        try {
            val token = withContext(Dispatchers.IO) { UserPreferences(navController.context).getAccessToken() }.orEmpty()
            val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)
            val res = withContext(Dispatchers.IO) { api.getPlan(planId) }
            if (res.isSuccessful) {
                val dto = res.body()
                name = dto?.name.orEmpty().sanitizeText(MAX_PLAN_NAME)
                isPublic = dto?.isPublic ?: false
                days.clear()
                dto?.days.orEmpty().forEach { d ->
                    days.add(
                        DayUi(
                            title = (d.title ?: "").sanitizeText(MAX_DAY_TITLE).ifBlank { "Dzień" },
                            exercises = d.exercises.map {
                                ExerciseUi(
                                    name = (it.name ?: "Ćwiczenie").sanitizeText(MAX_EXERCISE_NAME).ifBlank { "Ćwiczenie" },
                                    sets = clampInt(it.sets ?: 3, MIN_SETS, MAX_SETS),
                                    reps = clampInt(it.reps ?: 10, MIN_REPS, MAX_REPS)
                                )
                            }.toMutableList()
                        )
                    )
                }
            } else {
                snackbar.showSnackbar("Błąd pobierania: ${res.code()}")
            }
        } catch (e: Exception) {
            snackbar.showSnackbar(e.localizedMessage ?: "Błąd sieci")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<ArrayList<String>?>(EX_RESULT_IDS, null).collect { ids ->
            if (ids.isNullOrEmpty()) return@collect
            val names = handle.get<ArrayList<String>>(EX_RESULT_NAMES)
            val dayIdx = pendingAddForDayIndex
            if (dayIdx != null && dayIdx in days.indices) {
                ids.forEachIndexed { idx, _ ->
                    val rawName = names?.getOrNull(idx) ?: "Ćwiczenie"
                    val exName = rawName.sanitizeText(MAX_EXERCISE_NAME).ifBlank { "Ćwiczenie" }
                    days[dayIdx].exercises.add(ExerciseUi(name = exName))
                }
            }
            pendingAddForDayIndex = null
            handle[EX_RESULT_IDS] = null
            handle[EX_RESULT_NAMES] = null
        }
    }

    fun savePlan() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { UserPreferences(navController.context).getAccessToken() }.orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)

                val safeName = name.sanitizeText(MAX_PLAN_NAME)
                val safeDays = days.map { d ->
                    val safeTitle = d.title.sanitizeText(MAX_DAY_TITLE).ifBlank { "Dzień" }
                    val safeExercises = d.exercises.map { e ->
                        val safeExName = e.name.sanitizeText(MAX_EXERCISE_NAME).ifBlank { "Ćwiczenie" }
                        val safeSets = clampInt(e.sets, MIN_SETS, MAX_SETS)
                        val safeReps = clampInt(e.reps, MIN_REPS, MAX_REPS)

                        ExerciseRequest(
                            name = safeExName,
                            muscleGroup = "Nieznana",
                            sets = safeSets,
                            repsMin = safeReps,
                            repsMax = safeReps,
                            rir = 1
                        )
                    }

                    TrainingPlanDayCreateDto(
                        title = safeTitle,
                        exercises = safeExercises
                    )
                }

                val dto = TrainingPlanUpdateRequest(
                    name = safeName.ifBlank { null },
                    isPublic = isPublic,
                    days = safeDays
                )

                val res = withContext(Dispatchers.IO) { api.updatePlan(planId, dto) }
                if (res.isSuccessful) {
                    snackbar.showSnackbar("Zapisano plan")
                    navController.navigateUp()
                } else {
                    snackbar.showSnackbar("Błąd zapisu: ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.localizedMessage ?: "Błąd sieci")
            } finally {
                saving = false
            }
        }
    }

    fun deletePlan() {
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { UserPreferences(navController.context).getAccessToken() }.orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)
                val res = withContext(Dispatchers.IO) { api.deletePlan(planId) }
                if (res.isSuccessful) {
                    snackbar.showSnackbar("Usunięto plan")
                    navController.popBackStack(route = "plans", inclusive = false)
                } else {
                    snackbar.showSnackbar("Błąd usuwania: ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.localizedMessage ?: "Błąd sieci")
            } finally {
                confirmDelete = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Edytor planu") },
                actions = {
                    IconButton(onClick = { savePlan() }, enabled = !saving) {
                        Icon(Icons.Filled.Save, contentDescription = "Zapisz")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń plan")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    days.add(DayUi(title = "Dzień ${days.size + 1}"))
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj dzień") }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.sanitizeText(MAX_PLAN_NAME) },
                                label = { Text("Nazwa planu") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                supportingText = { Text("${name.length}/$MAX_PLAN_NAME") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            AssistChip(onClick = {}, enabled = false, label = { Text("Dni: ${days.size}") })
                        }
                    }
                }

                itemsIndexed(days, key = { idx, _ -> "day_$idx" }) { index, day ->
                    DayEditorCard(
                        dayIndex = index,
                        canMoveDown = index < days.lastIndex,
                        day = day,
                        onMoveUp = {
                            if (index > 0) {
                                val tmp = days[index]
                                days[index] = days[index - 1]
                                days[index - 1] = tmp
                            }
                        },
                        onMoveDown = {
                            if (index < days.lastIndex) {
                                val tmp = days[index]
                                days[index] = days[index + 1]
                                days[index + 1] = tmp
                            }
                        },
                        onTitleChange = { newTitle ->
                            val safe = newTitle.sanitizeText(MAX_DAY_TITLE)
                            days[index] = days[index].copy(title = safe)
                        },
                        onDeleteDay = { days.removeAt(index) },
                        onAddExercises = {
                            pendingAddForDayIndex = index
                            navController.navigate("exercisePicker")
                        },
                        onDeleteExercise = { exIdx ->
                            if (exIdx in days[index].exercises.indices) {
                                days[index].exercises.removeAt(exIdx)
                            }
                        },
                        onEditExercise = { exIdx, sets, reps ->
                            if (exIdx in days[index].exercises.indices) {
                                val e = days[index].exercises[exIdx]
                                days[index].exercises[exIdx] = e.copy(
                                    sets = clampInt(sets, MIN_SETS, MAX_SETS),
                                    reps = clampInt(reps, MIN_REPS, MAX_REPS)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć plan?") },
            text = { Text("Tej operacji nie można cofnąć.") },
            confirmButton = { TextButton(onClick = { deletePlan() }) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } }
        )
    }
}


@Composable
private fun DayEditorCard(
    dayIndex: Int,
    canMoveDown: Boolean,
    day: DayUi,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDeleteDay: () -> Unit,
    onAddExercises: () -> Unit,
    onDeleteExercise: (exIndex: Int) -> Unit,
    onEditExercise: (exIndex: Int, sets: Int, reps: Int) -> Unit
) {
    var editTitle by remember(day.title) { mutableStateOf(day.title) }

    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Nagłówek + akcje
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = {
                        val safe = it.sanitizeText(MAX_DAY_TITLE)
                        editTitle = safe
                        onTitleChange(safe)
                    },
                    label = { Text("Tytuł dnia") },
                    singleLine = true,
                    supportingText = { Text("${editTitle.length}/$MAX_DAY_TITLE") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onMoveUp, enabled = dayIndex > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Do góry")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "W dół")
                }
                IconButton(onClick = onDeleteDay) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń dzień")
                }
            }

            if (day.exercises.isEmpty()) {
                Text(
                    "Brak ćwiczeń — dodaj pierwsze.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                day.exercises.forEachIndexed { exIndex, ex ->
                    ExerciseRow(
                        exercise = ex,
                        onDelete = { onDeleteExercise(exIndex) },
                        onEdit = { sets, reps -> onEditExercise(exIndex, sets, reps) }
                    )
                    if (exIndex != day.exercises.lastIndex) Divider()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAddExercises) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Dodaj ćwiczenia")
                }
            }
        }
    }
}


@Composable
private fun ExerciseRow(
    exercise: ExerciseUi,
    onDelete: () -> Unit,
    onEdit: (sets: Int, reps: Int) -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Text("${exercise.sets}×${exercise.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row {
            IconButton(onClick = { showEdit = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edytuj serie/powt.")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Usuń ćwiczenie")
            }
        }
    }

    if (showEdit) {
        var setsTxt by remember(exercise.sets) { mutableStateOf(exercise.sets.toString()) }
        var repsTxt by remember(exercise.reps) { mutableStateOf(exercise.reps.toString()) }

        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Ustawienia serii/powtórzeń") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setsTxt,
                        onValueChange = { setsTxt = it.onlyDigits(2) }, // 1..99
                        label = { Text("Serie ($MIN_SETS–$MAX_SETS)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repsTxt,
                        onValueChange = { repsTxt = it.onlyDigits(3) }, // 1..999
                        label = { Text("Powtórzenia ($MIN_REPS–$MAX_REPS)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sets = clampInt(setsTxt.toIntOrNull() ?: exercise.sets, MIN_SETS, MAX_SETS)
                    val reps = clampInt(repsTxt.toIntOrNull() ?: exercise.reps, MIN_REPS, MAX_REPS)
                    onEdit(sets, reps)
                    showEdit = false
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Anuluj") } }
        )
    }
}
