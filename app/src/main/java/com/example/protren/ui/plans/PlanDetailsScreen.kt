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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.model.TrainingPlan
import com.example.protren.model.TrainingPlanDay
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainingPlanApi
import com.example.protren.viewmodel.PlanDetailsViewModel
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlanDetailsScreen(
    navController: NavController,
    planId: String
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = remember { com.example.protren.data.UserPreferences(ctx) }

    fun deletePlan() {
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { prefs.getAccessToken() }.orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)
                val response = withContext(Dispatchers.IO) { api.deletePlan(planId) }

                if (response.isSuccessful) {
                    navController.popBackStack()
                } else {
                    snackbar.showSnackbar("Błąd podczas usuwania planu")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Błąd sieci: ${e.message}")
            }
        }
    }
    val vm: PlanDetailsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlanDetailsViewModel(ctx.applicationContext) as T
    })

    val plan by vm.plan.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(planId) { vm.load(planId) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, planId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.load(planId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    IconButton(onClick = { navController.navigate("planEditor/$planId") }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edytuj plan")
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = day.title,
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
                        contentDescription = if (expanded) "Zwiń" else "Rozwiń"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (day.exercises.isEmpty()) {
                        Text(
                            "Brak ćwiczeń w tym dniu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        day.exercises.forEachIndexed { idx, ex ->
                            Column {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${ex.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${ex.sets ?: 0}×${ex.reps ?: 0}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (idx != day.exercises.lastIndex) Divider(Modifier.padding(top = 8.dp))
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onStartDay,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rozpocznij trening")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Plan nie ma jeszcze dni.",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Wejdź w edycję i dodaj dzień oraz ćwiczenia.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
