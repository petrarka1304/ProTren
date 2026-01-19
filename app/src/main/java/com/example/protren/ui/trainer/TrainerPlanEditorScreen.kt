@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.trainer

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES
import com.example.protren.viewmodel.ExerciseUi
import com.example.protren.viewmodel.PlanEditorState
import com.example.protren.viewmodel.TrainerPlanEditorViewModel
import kotlinx.coroutines.launch

@Composable
fun TrainerPlanEditorScreen(
    nav: NavHostController,
    planId: String
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val vm: TrainerPlanEditorViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainerPlanEditorViewModel(app) as T
    })

    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(planId) {
        val current = vm.state.value
        if (current !is PlanEditorState.Loaded || current.id != planId) {
            vm.load(planId)
        }
    }

    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val savedStateHandle = navBackStackEntry?.savedStateHandle

    val pickedIds: ArrayList<String>? =
        savedStateHandle?.get(EXERCISE_PICKER_RESULT_IDS)
    val pickedNames: ArrayList<String>? =
        savedStateHandle?.get(EXERCISE_PICKER_RESULT_NAMES)

    LaunchedEffect(pickedIds, pickedNames) {
        if (pickedIds != null) {
            val dayIndex = savedStateHandle?.get<Int>("planEditor_dayIndex") ?: -1

            if (dayIndex >= 0) {
                vm.setExercisesForDay(
                    index = dayIndex,
                    ids = pickedIds,
                    names = pickedNames ?: arrayListOf()
                )
            }

            savedStateHandle?.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_IDS)
            savedStateHandle?.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Edycja planu") },
                actions = {
                    IconButton(onClick = { vm.addDay() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Dodaj dzień")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when (val s = state) {
            PlanEditorState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is PlanEditorState.Error -> {
                val msg = (s as PlanEditorState.Error).message
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Nie udało się wczytać planu",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.reload() }) { Text("Spróbuj ponownie") }
                }
            }

            is PlanEditorState.Loaded -> {
                var name by remember(s) { mutableStateOf(s.name) }
                var lastSaveInfo by remember { mutableStateOf<String?>(null) }

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ElevatedCard(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Nazwa planu", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    vm.rename(it)
                                    lastSaveInfo = null
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    val days = s.days
                    if (days.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Brak dni w planie",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Dodaj pierwszy dzień przyciskiem u góry.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { vm.addDay() }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dodaj dzień")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(days) { index, day ->
                                DayCard(
                                    index = index,
                                    title = day.title,
                                    exercisesCount = day.exercisesCount,
                                    exercises = day.exercises,
                                    onTitleChange = {
                                        vm.renameDay(index, it)
                                        lastSaveInfo = null
                                    },
                                    onEditExercises = {
                                        nav.currentBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("planEditor_dayIndex", index)
                                        nav.navigate("exercisePicker")
                                    },
                                    onExerciseChange = { exIndex, updated ->
                                        vm.updateExerciseInDay(index, exIndex, updated)
                                        lastSaveInfo = null
                                    },
                                    onRemove = { vm.removeDay(index) }
                                )
                            }

                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }

                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Zmiany nie zapisują się automatycznie",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                lastSaveInfo?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    vm.save { ok, msg ->
                                        scope.launch { snackbar.showSnackbar(msg) }
                                        lastSaveInfo =
                                            if (ok) "Zapisano zmiany w planie" else null
                                        if (ok) nav.navigateUp()
                                    }
                                }
                            ) {
                                Text("Zapisz zmiany")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    index: Int,
    title: String,
    exercisesCount: Int,
    exercises: List<ExerciseUi>,
    onTitleChange: (String) -> Unit,
    onEditExercises: () -> Unit,
    onExerciseChange: (Int, ExerciseUi) -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        singleLine = true,
                        label = { Text("Tytuł dnia") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = onEditExercises,
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                        },
                        label = {
                            val label = when (exercisesCount) {
                                0 -> "Brak ćwiczeń"
                                1 -> "1 ćwiczenie"
                                else -> "$exercisesCount ćwiczeń"
                            }
                            Text(label)
                        }
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń dzień")
                }
            }

            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercises.forEachIndexed { exIndex, ex ->
                        ExerciseRowEditor(
                            exercise = ex,
                            onChange = { updated -> onExerciseChange(exIndex, updated) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRowEditor(
    exercise: ExerciseUi,
    onChange: (ExerciseUi) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            exercise.name,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = if (exercise.sets == 0) "" else exercise.sets.toString(),
                onValueChange = { input ->
                    val cleanInput = input.filter { it.isDigit() }.take(3)

                    val newValue = cleanInput.toIntOrNull() ?: 0
                    onChange(exercise.copy(sets = newValue))
                },
                label = { Text("Serie") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // Klawiatura numeryczna
                modifier = Modifier.width(80.dp)
            )

            OutlinedTextField(
                value = if (exercise.reps == 0) "" else exercise.reps.toString(),
                onValueChange = { input ->
                    val cleanInput = input.filter { it.isDigit() }.take(3)
                    val newValue = cleanInput.toIntOrNull() ?: 0
                    onChange(exercise.copy(reps = newValue))
                },
                label = { Text("Powt.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp)
            )

            OutlinedTextField(

                value = if (exercise.weight == 0f) "" else exercise.weight.toString().removeSuffix(".0"),
                onValueChange = { input ->

                    var cleanInput = input.replace(",", ".")

                    // Zabezpieczenie
                    if (cleanInput.length <= 6 && cleanInput.count { it == '.' } <= 1) {
                        cleanInput = cleanInput.filter { it.isDigit() || it == '.' }

                        val newValue = cleanInput.toFloatOrNull() ?: 0f
                        onChange(exercise.copy(weight = newValue))
                    }
                },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), // Klawiatura dziesiętna
                modifier = Modifier.width(100.dp)
            )
        }
    }
}