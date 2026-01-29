@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.protren.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.protren.data.UserPreferences
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES

@Composable
fun TrackedExercisesScreen(navController: NavController) {

    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    var trackedExercises by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        trackedExercises = prefs.getTrackedExercises()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect

        handle.getStateFlow<ArrayList<String>?>(EXERCISE_PICKER_RESULT_NAMES, null)
            .collect { picked ->
                if (!picked.isNullOrEmpty()) {

                    val current = prefs.getTrackedExercises().toMutableSet()
                    current.addAll(picked)
                    val newList = current.toList()
                    prefs.saveTrackedExercises(newList)
                    trackedExercises = newList
                    handle[EXERCISE_PICKER_RESULT_NAMES] = null
                }
            }
    }

    fun removeExercise(name: String) {
        val updated = trackedExercises.toMutableList()
        updated.remove(name)
        prefs.saveTrackedExercises(updated)
        trackedExercises = updated
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Śledzone ćwiczenia") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ćwiczenia do śledzenia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Progres liczony jest na podstawie wykonanych treningów.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { navController.navigate("exercisePicker") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Dodaj")
                        }

                        Button(
                            onClick = { navController.navigate("trackedExercisesProgress") },
                            enabled = trackedExercises.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ShowChart, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Progres")
                        }
                    }

                    if (trackedExercises.isNotEmpty()) {
                        Text(
                            text = "Wybrane: ${trackedExercises.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (trackedExercises.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nie wybrano żadnych ćwiczeń.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Lista ćwiczeń",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(trackedExercises, key = { it }) { name ->
                        ElevatedCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 14.dp,
                                        top = 8.dp,
                                        bottom = 8.dp,
                                        end = 8.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                IconButton(onClick = { removeExercise(name) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Usuń ćwiczenie",
                                        tint = MaterialTheme.colorScheme.error
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
