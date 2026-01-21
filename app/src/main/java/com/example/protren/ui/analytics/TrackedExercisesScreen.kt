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
import com.example.protren.data.UserPreferences
import com.example.protren.ui.exercises.EXERCISE_PICKER_RESULT_NAMES

@Composable
fun TrackedExercisesScreen(navController: NavController) {

    val context = LocalContext.current
    // Pamiętamy instancję preferences, ale dane w niej są żywe
    val prefs = remember { UserPreferences(context) }

    // Wczytujemy początkowy stan
    var trackedExercises by remember {
        mutableStateOf(prefs.getTrackedExercises())
    }

    val handle = navController.currentBackStackEntry?.savedStateHandle

    // --- KLUCZOWA ZMIANA TUTAJ ---
    LaunchedEffect(handle) {
        // Obserwujemy wynik z ekranu wyboru
        val pickedFlow = handle?.getStateFlow<ArrayList<String>?>(EXERCISE_PICKER_RESULT_NAMES, null)

        pickedFlow?.collect { picked ->
            if (!picked.isNullOrEmpty()) {
                // 1. Pobieramy aktualną listę
                val currentList = prefs.getTrackedExercises().toMutableSet() // Set zapobiega duplikatom

                // 2. Dodajemy nowe (zamiast nadpisywać)
                currentList.addAll(picked)

                // 3. Zapisujemy połączoną listę
                val newList = currentList.toList()
                prefs.saveTrackedExercises(newList)

                // 4. Aktualizujemy widok
                trackedExercises = newList

                // 5. Czyścimy wynik, aby nie dodał się ponownie przy obrocie ekranu itp.
                handle.remove<ArrayList<String>>(EXERCISE_PICKER_RESULT_NAMES)
            }
        }
    }

    // Funkcja pomocnicza do usuwania pojedynczego ćwiczenia
    fun removeExercise(name: String) {
        val currentList = trackedExercises.toMutableList()
        currentList.remove(name)
        prefs.saveTrackedExercises(currentList)
        trackedExercises = currentList
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
                        text = "Progres liczony jest na podstawie wykonanych treningów z ostatnich 30 dni.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                navController.navigate("exercisePicker")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Dodaj") // Zmieniłem "Wybierz" na "Dodaj" dla jasności
                        }

                        Button(
                            onClick = {
                                navController.navigate("trackedExercisesProgress")
                            },
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
                                    .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                // Dodałem przycisk usuwania
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