@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.protren.ui.trainer

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.model.Exercise
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_IDS
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES
import com.example.protren.viewmodel.DayPlanUi
import com.example.protren.viewmodel.PeriodPlanState
import com.example.protren.viewmodel.TrainerPanelViewModel
import com.example.protren.viewmodel.TrainerPeriodPlanViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrainerPeriodPlanScreen(
    nav: NavController,
    traineeId: String? = null
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: TrainerPeriodPlanViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainerPeriodPlanViewModel(app) as T
    })

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fmt = remember { DateTimeFormatter.ISO_DATE }

    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var weeks by remember { mutableStateOf(4) }
    var planName by remember { mutableStateOf("Plan okresowy") }

    var showPicker by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val state by vm.state.collectAsState()

    LaunchedEffect(startDate, weeks) { vm.buildCalendar(startDate, weeks) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Plan okresowy") },
                actions = {
                    IconButton(
                        onClick = {
                            vm.buildCalendar(startDate, weeks)
                            scope.launch { snackbar.showSnackbar("Przebudowano kalendarz") }
                        }
                    ) { Icon(Icons.Filled.Refresh, contentDescription = "Odśwież") }
                },
                windowInsets = WindowInsets(0),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            BottomActionBar(
                traineeId = traineeId,
                planName = planName,
                onSaveTemplate = {
                    vm.saveAsTrainerTemplate(planName) { ok, msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                        if (ok) nav.navigateUp()
                    }
                },
                onAssign = {
                    if (traineeId != null) {
                        vm.assignToTrainee(traineeId, planName) { ok, msg ->
                            scope.launch { snackbar.showSnackbar(msg) }
                            if (ok) nav.navigateUp()
                        }
                    } else showPicker = true
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { PlanBanner(planName, startDate, weeks, fmt) }

                item {
                    PlanParamsCard(
                        startDate = startDate,
                        onStartDateInfo = { showDatePicker = true },
                        weeks = weeks,
                        onWeeksChange = { weeks = it.coerceIn(1, 26) },
                        planName = planName,
                        onPlanNameChange = { planName = it },
                        onRebuild = {
                            vm.buildCalendar(startDate, weeks)
                            scope.launch { snackbar.showSnackbar("Przebudowano kalendarz") }
                        }
                    )
                }

                item {
                    TraineeInfoOrPicker(
                        hasTrainee = traineeId != null,
                        onPick = { showPicker = true }
                    )
                }

                when (val s = state) {
                    PeriodPlanState.Loading -> item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is PeriodPlanState.Error -> item {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }

                    is PeriodPlanState.Ready -> {
                        item {
                            Text("Kalendarz treningów", style = MaterialTheme.typography.titleMedium)
                            WeekHeaderRow()
                            Spacer(Modifier.height(8.dp))
                        }

                        item {
                            DaysGrid(
                                items = s.days,
                                onEditDay = { editingDate = it }
                            )
                        }
                    }

                    PeriodPlanState.Idle -> item {
                        Text(
                            "Stan Idle (kalendarz nie wygenerowany).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            editingDate?.let { date ->
                DayEditorSheet(
                    nav = nav,
                    vm = vm,
                    date = date,
                    snackbar = snackbar,
                    onClose = { editingDate = null }
                )
            }

            if (showPicker) {
                TraineePickerSheet(
                    onSelect = { userId, _ ->
                        showPicker = false
                        vm.assignToTrainee(userId, planName) { ok, msg ->
                            scope.launch { snackbar.showSnackbar(msg) }
                            if (ok) nav.navigateUp()
                        }
                    },
                    onClose = { showPicker = false }
                )
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = startDate.toEpochDay() * 24L * 60L * 60L * 1000L
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val millis = datePickerState.selectedDateMillis
                            if (millis != null) {
                                startDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                vm.buildCalendar(startDate, weeks)
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") } }
                ) { DatePicker(state = datePickerState) }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    traineeId: String?,
    planName: String,
    onSaveTemplate: () -> Unit,
    onAssign: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onSaveTemplate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zapisz szablon", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Button(
                onClick = onAssign,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.PersonAddAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (traineeId != null) "Zapisz dla podopiecznego" else "Przypisz podopiecznemu",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlanBanner(
    planName: String,
    startDate: LocalDate,
    weeks: Int,
    fmt: DateTimeFormatter
) {
    val endDate = remember(startDate, weeks) { startDate.plusWeeks(weeks.toLong()).minusDays(1) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Plan okresowy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Text(
                text = if (planName.isBlank()) "Bez nazwy" else planName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text("${startDate.format(fmt)} – ${endDate.format(fmt)}") },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${weeks.coerceIn(1, 26)} tyg.") },
                    leadingIcon = { Icon(Icons.Filled.FitnessCenter, null) }
                )
            }
        }
    }
}

@Composable
private fun PlanParamsCard(
    startDate: LocalDate,
    onStartDateInfo: () -> Unit,
    weeks: Int,
    onWeeksChange: (Int) -> Unit,
    planName: String,
    onPlanNameChange: (String) -> Unit,
    onRebuild: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Parametry planu", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ustaw datę startu, długość i nazwę",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = startDate.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Start") },
                    trailingIcon = {
                        IconButton(onClick = onStartDateInfo) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                WeeksStepper(weeks = weeks, onWeeksChange = onWeeksChange)
            }

            OutlinedTextField(
                value = planName,
                onValueChange = onPlanNameChange,
                label = { Text("Nazwa planu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FilledTonalButton(
                onClick = onRebuild,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Przebuduj kalendarz")
            }
        }
    }
}

@Composable
private fun WeeksStepper(
    weeks: Int,
    onWeeksChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { onWeeksChange((weeks - 1).coerceAtLeast(1)) },
                enabled = weeks > 1
            ) { Icon(Icons.Filled.Remove, contentDescription = "Mniej tygodni") }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tyg.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            IconButton(
                onClick = { onWeeksChange((weeks + 1).coerceAtMost(26)) },
                enabled = weeks < 26
            ) { Icon(Icons.Filled.Add, contentDescription = "Więcej tygodni") }
        }
    }
}

@Composable
private fun TraineeInfoOrPicker(
    hasTrainee: Boolean,
    onPick: () -> Unit
) {
    if (hasTrainee) {
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Wybrano podopiecznego", fontWeight = FontWeight.SemiBold)
                    Text("Możesz zapisać plan dla tej osoby.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.PersonSearch, null)
            Spacer(Modifier.width(8.dp))
            Text("Wybierz podopiecznego…")
        }
    }
}

@Composable
private fun WeekHeaderRow() {
    val names = remember { listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Niedz") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        names.forEach { n ->
            Box(
                modifier = Modifier.weight(1f).height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(n, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DaysGrid(
    items: List<DayPlanUi>,
    onEditDay: (LocalDate) -> Unit
) {
    val dayFmt = remember { DateTimeFormatter.ofPattern("d") }
    val today = remember { LocalDate.now() }
    val locale = remember { Locale("pl") }

    val rows = remember(items.size) { ((items.size + 6) / 7).coerceAtLeast(1) }
    val spacing = 8.dp
    val cell = 64.dp
    val gridHeight = remember(rows) { cell * rows + spacing * (rows - 1) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(items, key = { it.date }) { day ->
            val hasExercises = day.exercises.isNotEmpty()
            val isToday = day.date == today

            val container =
                when {
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    hasExercises -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

            val onContainer =
                when {
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    hasExercises -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

            val dow = remember(day.date) {
                day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale).uppercase(locale)
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                tonalElevation = if (hasExercises || isToday) 2.dp else 0.dp,
                color = container,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .animateContentSize()
                    .clickable { onEditDay(day.date) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dow,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.date.format(dayFmt),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasExercises) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${day.exercises.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onContainer,
                                maxLines = 1
                            )
                        } else {
                            Text(" ", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEditorSheet(
    nav: NavController,
    vm: TrainerPeriodPlanViewModel,
    date: LocalDate,
    snackbar: SnackbarHostState,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val s = state as? PeriodPlanState.Ready ?: return
    val day = s.days.firstOrNull { it.date == date } ?: return

    LaunchedEffect(Unit) {
        val handle = nav.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<ArrayList<String>?>(EXERCISE_PICKER_RESULT_IDS, null)
            .collect { ids ->
                val names = handle.get<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
                if (!ids.isNullOrEmpty()) {
                    val incoming = ids.mapIndexed { idx, _ ->
                        Exercise(
                            name = names?.getOrNull(idx) ?: "Ćwiczenie",
                            sets = 3,
                            reps = 10,
                            weight = 0,
                            notes = null
                        )
                    }
                    vm.addExercises(date, incoming)
                    scope.launch { snackbar.showSnackbar("Dodano ${incoming.size} ćwiczeń z katalogu.") }
                    handle[EXERCISE_PICKER_RESULT_IDS] = null
                    handle[EXERCISE_PICKER_RESULT_NAMES] = null
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        windowInsets = BottomSheetDefaults.windowInsets
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dzień $date", style = MaterialTheme.typography.titleMedium)

            var tab by remember { mutableStateOf(0) }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Trening") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notatki") })
            }

            when (tab) {
                0 -> {
                    if (day.exercises.isEmpty()) {
                        Text("Brak ćwiczeń w tym dniu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            day.exercises.forEachIndexed { index, ex ->
                                ExerciseRow(
                                    exercise = ex,
                                    onChange = { updated -> vm.updateExercise(date, index, updated) },
                                    onDelete = { vm.removeExercise(date, index) }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { nav.navigate("exercisePicker") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.LibraryAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Z katalogu")
                        }

                        FilledTonalButton(
                            onClick = {
                                vm.addExercise(
                                    date,
                                    Exercise(
                                        name = "Ćwiczenie",
                                        sets = 3,
                                        reps = 10,
                                        weight = 0,
                                        notes = null
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Edit, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Dodaj ręcznie")
                        }
                    }
                }

                1 -> {
                    OutlinedTextField(
                        value = day.notes,
                        onValueChange = { vm.updateDayNotes(date, it) },
                        label = { Text("Notatki dnia") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onChange: (Exercise) -> Unit,
    onDelete: () -> Unit
) {
    // Bufory tekstu – dzięki temu można wpisywać/usuwać normalnie
    var nameText by remember(exercise.name) { mutableStateOf(exercise.name ?: "") }
    var setsText by remember(exercise.sets) {
        mutableStateOf(exercise.sets?.takeIf { it > 0 }?.toString() ?: "")
    }
    var repsText by remember(exercise.reps) {
        mutableStateOf(exercise.reps?.takeIf { it > 0 }?.toString() ?: "")
    }
    var weightText by remember(exercise.weight) {
        mutableStateOf(exercise.weight?.toString() ?: "")
    }
    var notesText by remember(exercise.notes) { mutableStateOf(exercise.notes ?: "") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = nameText,
                onValueChange = {
                    nameText = it
                    onChange(exercise.copy(name = it))
                },
                label = { Text("Ćwiczenie") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = setsText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }.take(3)
                        setsText = clean
                        onChange(exercise.copy(sets = clean.toIntOrNull()))
                    },
                    label = { Text("Serie") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = repsText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }.take(3)
                        repsText = clean
                        onChange(exercise.copy(reps = clean.toIntOrNull() ?: 0))
                    },
                    label = { Text("Powtórzenia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                // WERSJA DLA weight = Int? (tak jak masz teraz w tym ekranie)
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }.take(5)
                        weightText = clean
                        onChange(exercise.copy(weight = clean.toIntOrNull() ?: 0))
                    },
                    label = { Text("Ciężar") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    onChange(exercise.copy(notes = it))
                },
                label = { Text("Notatki do ćwiczenia") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Usuń")
                }
            }
        }
    }
}


@Composable
private fun TraineePickerSheet(
    onSelect: (userId: String, name: String) -> Unit,
    onClose: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: TrainerPanelViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainerPanelViewModel(app) as T
    })

    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val items by vm.trainees.collectAsState()

    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) items else items.filter { t ->
            t.name.lowercase().contains(q) || t.email.lowercase().contains(q)
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    ModalBottomSheet(
        onDismissRequest = onClose,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Wybierz podopiecznego", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Szukaj po imieniu lub emailu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                loading -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight(0.7f)
                    ) {
                        items(filtered.size) { idx ->
                            val t = filtered[idx]
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(t.userId, t.name) }
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t.name.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(t.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
