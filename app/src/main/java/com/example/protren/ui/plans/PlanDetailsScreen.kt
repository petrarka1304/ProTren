@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.plans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.model.TrainingPlan
import com.example.protren.model.TrainingPlanDay
import com.example.protren.viewmodel.PlanDetailsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlinx.coroutines.launch

@Composable
fun PlanDetailsScreen(
    navController: NavController,
    planId: String
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current
    val vm: PlanDetailsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlanDetailsViewModel(ctx.applicationContext) as T
    })

    val plan by vm.plan.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(planId) { vm.load(planId) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text(plan?.name ?: "Mój plan") },
                actions = {
                    // ✏️ otwiera ekran edycji planu
                    IconButton(onClick = { navController.navigate("planEditor/$planId") }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edytuj plan")
                    }
                    IconButton(onClick = { /* TODO: potwierdzenie i usuwanie planu */ }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń plan")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when {
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }

            plan == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> {
                val p = plan!!

                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item { PlanHeaderCard(p) }

                    itemsIndexed(p.days) { index, day ->
                        DayCard(
                            plan = p,
                            day = day,
                            dayNumber = index + 1,
                            onStartDay = {
                                vm.startWorkoutForDay(
                                    plan = p,
                                    day = day
                                ) { ok, msg, id ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            msg ?: if (ok) "Trening zapisany" else "Nie udało się zapisać"
                                        )
                                    }
                                    if (ok && id != null) {
                                        navController.navigate("editWorkout/$id") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        )
                    }

                    if (p.days.isEmpty()) {
                        item { EmptyHint() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanHeaderCard(plan: TrainingPlan) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                plan.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            val tag = remember(plan.days) {
                val titles = plan.days.joinToString(" ") { it.title }.lowercase()
                when {
                    "full" in titles -> "Full Body"
                    listOf("upper", "góra").any { it in titles } &&
                            listOf("lower", "dół", "nogi").any { it in titles } -> "Upper / Lower"
                    listOf("push", "pull", "legs", "nogi").count { it in titles } >= 2 -> "PPL"
                    else -> "Plan niestandardowy"
                }
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(tag) }
            )

            val totalExercises = plan.days.sumOf { it.exercises.size }
            val totalSets = plan.days.sumOf { day ->
                day.exercises.sumOf { e -> (e.sets ?: 0) }
            }
            val estReps = plan.days.sumOf { day ->
                day.exercises.sumOf { e -> (e.sets ?: 0) * max(e.reps ?: 0, 1) }
            }

            Text(
                "Dni: ${plan.days.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Ćwiczeń łącznie: $totalExercises",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Szac. objętość (serie/powt.): $totalSets / $estReps",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayCard(
    plan: TrainingPlan,
    day: TrainingPlanDay,
    dayNumber: Int,
    onStartDay: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Nagłówek dnia + podsumowanie
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dzień $dayNumber • ${day.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val totalSets = remember(day) {
                        day.exercises.sumOf { it.sets ?: 0 }
                    }

                    Text(
                        text = "${day.exercises.size} ćwiczeń • $totalSets serii",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Zwiń dzień" else "Rozwiń dzień"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeaderRow()
                    Divider()

                    day.exercises.forEach { e ->
                        val name = e.name ?: ""
                        val sets = e.sets ?: 0
                        val reps = e.reps ?: 0

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, Modifier.weight(1f))
                            Text("$sets", Modifier.width(56.dp), textAlign = TextAlign.End)
                            Text("$reps", Modifier.width(56.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Data: $today",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onStartDay,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Rozpocznij dzień")
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Ćwiczenie",
            Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        Text(
            "Serie",
            Modifier.width(56.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Powt.",
            Modifier.width(56.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyHint() {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Brak dni w planie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Ten plan nie ma jeszcze zdefiniowanych dni. Dodaj je w generatorze albo edytorze planu.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
