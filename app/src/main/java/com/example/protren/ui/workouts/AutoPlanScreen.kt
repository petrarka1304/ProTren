package com.example.protren.ui.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.protren.data.ExerciseRepository
import com.example.protren.data.UserPreferences
import com.example.protren.logic.*
import com.example.protren.network.ApiClient
import com.example.protren.network.ExerciseApi
import com.example.protren.network.TrainingPlanApi
import com.example.protren.viewmodel.AutoPlanViewModel
import com.example.protren.viewmodel.AutoPlanVmFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlanScreen(navController: NavHostController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val retrofit = remember {
        ApiClient.createWithAuth(
            tokenProvider = { UserPreferences(ctx).getAccessToken() ?: "" },
            onUnauthorized = {}
        )
    }

    val exerciseRepo = remember { ExerciseRepository(retrofit.create(ExerciseApi::class.java)) }
    val planApi = remember { retrofit.create(TrainingPlanApi::class.java) }
    val vm: AutoPlanViewModel = viewModel(factory = AutoPlanVmFactory(exerciseRepo, planApi))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar(
                canSave = vm.plan != null,
                onGenerate = vm::generate,
                onClear = vm::clearPlan,
                onSave = {
                    scope.launch {
                        val (ok, msg) = vm.savePlan()
                        snackbar.showSnackbar(msg)
                        if (ok) {
                            navController.navigate("plans") {
                                popUpTo("autoPlan") { inclusive = true }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            vm.loading -> CenterLoader(padding)
            vm.error != null -> ErrorState(vm.error!!, vm::reload, padding)
            else -> AutoPlanContent(vm, padding)
        }
    }
}

/* ================= CONTENT ================= */

@Composable
private fun AutoPlanContent(vm: AutoPlanViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Header() }

        item {
            ConfigSection("Struktura treningów", Icons.Filled.Tune) {
                PlanTypePicker(vm.options, vm::updateOptions)
                DaysSlider(vm.options, vm::updateOptions)
                WeeksSlider(vm.options, vm::updateOptions)
            }
        }

        item {
            ConfigSection("Poziom i sprzęt", Icons.Filled.FitnessCenter) {
                LevelPicker(vm.options, vm::updateOptions)
                EquipmentPicker(vm.options, vm::updateOptions)
            }
        }

        item {
            ConfigSection("Cel treningowy", Icons.Filled.Flag) {
                GoalPicker(vm.options, vm::updateOptions)
            }
        }

        item {
            AnimatedVisibility(visible = vm.plan != null) {
                vm.plan?.let { PlanTimeline(it) }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

/* ================= HEADER ================= */

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Automatyczny plan treningowy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Ustaw parametry, a dostaniesz plan, który Cię poprowadzi!",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ================= CONFIG ================= */

@Composable
private fun ConfigSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Divider()
            content()
        }
    }
}

/* ================= PICKERS ================= */

@Composable
private fun LevelPicker(opt: GenerationOptions, onChange: (GenerationOptions) -> Unit) {
    SegmentedEqual(
        label = "Poziom zaawansowania",
        value = opt.level,
        items = listOf(
            "Początkujący" to Level.BEGINNER,
            "Średniozaaw." to Level.INTERMEDIATE,
            "Zaawansowany" to Level.ADVANCED
        )
    ) { onChange(opt.copy(level = it)) }
}

@Composable
private fun GoalPicker(opt: GenerationOptions, onChange: (GenerationOptions) -> Unit) {
    // ✅ wszystkie równe: maxLines=1 + elipsa + stała wysokość
    SegmentedEqual(
        label = "Cel planu",
        value = opt.goal,
        items = listOf(
            "Siła" to Goal.STRENGTH,
            "Masa" to Goal.HYPERTROPHY,
            "Redukcja" to Goal.FAT_LOSS,
            "Wytrzymałość" to Goal.ENDURANCE
        )
    ) { onChange(opt.copy(goal = it)) }
}

@Composable
private fun EquipmentPicker(
    opt: GenerationOptions,
    onChange: (GenerationOptions) -> Unit
) {
    SegmentedEqual(
        label = "Dostępny sprzęt",
        value = opt.equipment,
        items = listOf(
            "Dom" to Equipment.HOME,
            "Podstawowy" to Equipment.MINIMAL,
            "Siłownia" to Equipment.GYM
        )
    ) { onChange(opt.copy(equipment = it)) }
}

@Composable
private fun PlanTypePicker(
    opt: GenerationOptions,
    onChange: (GenerationOptions) -> Unit
) {
    SegmentedEqual(
        label = "Typ planu",
        value = opt.type,
        items = listOf(
            "Całe ciało" to PlanType.FULL_BODY,
            "Góra / dół" to PlanType.UPPER_LOWER,
            "Push / Pull / nogi" to PlanType.PUSH_PULL_LEGS
        )
    ) { onChange(opt.copy(type = it)) }
}

/* ================= SEGMENTED (RÓWNE) ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedEqual(
    label: String,
    value: T,
    items: List<Pair<String, T>>,
    onPick: (T) -> Unit
) {
    val minH = 48.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEachIndexed { i, (text, v) ->
                SegmentedButton(
                    selected = v == value,
                    onClick = { onPick(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, items.size),
                    modifier = Modifier
                        .weight(1f)
                        .height(minH)
                ) {
                    Text(
                        text = text,
                        textAlign = TextAlign.Center,
                        maxLines = 1,                 // ✅ nie rozpycha w pionie
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/* ================= SLIDERS ================= */

@Composable
private fun DaysSlider(opt: GenerationOptions, onChange: (GenerationOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Dni treningowe: ${opt.daysPerWeek}")
        Slider(
            value = opt.daysPerWeek.toFloat(),
            onValueChange = { onChange(opt.copy(daysPerWeek = it.toInt())) },
            valueRange = 2f..6f,
            steps = 3
        )
    }
}

@Composable
private fun WeeksSlider(opt: GenerationOptions, onChange: (GenerationOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Liczba tygodni: ${opt.numberOfWeeks}")
        Slider(
            value = opt.numberOfWeeks.toFloat(),
            onValueChange = { onChange(opt.copy(numberOfWeeks = it.toInt())) },
            valueRange = 1f..6f,
            steps = 4
        )
    }
}

/* ================= PLAN ================= */

@Composable
private fun PlanTimeline(plan: GeneratedPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Podgląd planu", style = MaterialTheme.typography.titleLarge)

        plan.microcycles.forEachIndexed { w, week ->
            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tydzień ${w + 1}", fontWeight = FontWeight.Bold)

                    week.forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(day.title, fontWeight = FontWeight.SemiBold)
                            day.exercises.forEach {
                                Text("• ${it.name}  ${it.sets}×${it.reps}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ================= BOTTOM ================= */

@Composable
private fun BottomActionBar(
    canSave: Boolean,
    onGenerate: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Surface(shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onClear,
                enabled = canSave
            ) {
                Icon(Icons.Filled.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text("Wyczyść", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onGenerate
            ) {
                Icon(Icons.Filled.AutoFixHigh, null)
                Spacer(Modifier.width(6.dp))
                Text("Generuj", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onSave,
                enabled = canSave
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Zapisz", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ================= STATES ================= */

@Composable
private fun CenterLoader(padding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun ErrorState(msg: String, onRetry: () -> Unit, padding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Błąd", fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}
