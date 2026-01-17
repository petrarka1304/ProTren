@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.protren.ui.supplements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.navigation.NavItem
import com.example.protren.viewmodel.SupplementsUIState
import com.example.protren.viewmodel.SupplementsViewModel
import kotlinx.coroutines.launch

@Composable
fun SupplementsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    // 👇 to jest TWÓJ ViewModel – w Twojej wersji przyjmuje prefs
    val vm = remember { SupplementsViewModel(prefs) }

    val ui by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // w Twoim oryginale tu było loadToday()
    LaunchedEffect(Unit) {
        vm.loadToday()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Suplementy na dziś") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    // przejście do ekranu zarządzania wszystkimi suplementami
                    IconButton(onClick = { navController.navigate("supplements/manage") }) {
                        Icon(Icons.Filled.List, contentDescription = "Wszystkie suplementy")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(NavItem.SupplementEditor) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj suplement") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when (val s = ui) {
            is SupplementsUIState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SupplementsUIState.Error -> {
                // w oryginale miałeś pokazanie snackbara – zostawiam
                LaunchedEffect(s.message) {
                    scope.launch { snackbar.showSnackbar(s.message) }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.loadToday() }) {
                            Text("Spróbuj ponownie")
                        }
                    }
                }
            }

            is SupplementsUIState.Loaded -> {
                val itemsToday = s.today
                if (itemsToday.isEmpty()) {
                    EmptyTodayState(
                        onManage = { navController.navigate("supplements/manage") },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                } else {
                    TodayList(
                        items = itemsToday,
                        onToggle = { id, take ->
                            vm.toggleToday(id, take) { ok ->
                                if (!ok) {
                                    scope.launch {
                                        snackbar.showSnackbar("Nie udało się zaktualizować stanu.")
                                    }
                                } else {
                                    // żeby się odświeżyło
                                    vm.loadToday()
                                }
                            }
                        },
                        onEdit = { id ->
                            // w oryginale miałeś na kartę: przejdź do edycji
                            navController.navigate("${NavItem.SupplementEditor}/$id")
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTodayState(
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Text("Dziś brak suplementów", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Dodaj suplement albo zmień plan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onManage) {
                    Text("Przejdź do listy")
                }
            }
        }
    }
}

private fun translateTime(code: String): String = when (code) {
    "morning" -> "Rano"
    "midday" -> "Południe"
    "evening" -> "Wieczór"
    "night" -> "Noc"
    else -> code
}

@Composable
private fun TodayList(
    items: List<Supplement>,
    onToggle: (id: String, take: Boolean) -> Unit,
    onEdit: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Dzisiejsze przypomnienia",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        items(
            items = items,
            key = { it._id.orEmpty() }
        ) { s ->
            val id = s._id.orEmpty()
            val name = s.name.orEmpty()
            val dosage = s.dosage.orEmpty()
            val times = (s.times ?: emptyList())
                .map { translateTime(it) }
                .joinToString(", ")
            val taken = (s.takenToday == true)

            // 👉 tu tylko dopieszczam wygląd:
            // - większe zaokrąglenie
            // - lekki "podskok" przy zmianie (animateContentSize)
            // - kliknięcie w całą kartę otwiera edycję
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(id) }
                    .animateItemPlacement()
                    .animateContentSize(animationSpec = tween(180))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val sub = listOfNotNull(
                                dosage.takeIf { it.isNotBlank() },
                                times.takeIf { it.isNotBlank() }
                            ).joinToString("  •  ")

                            AnimatedVisibility(visible = sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = taken) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // przyciski akcji
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (taken) {
                            FilledTonalButton(
                                onClick = { onToggle(id, false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Wzięte")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onToggle(id, true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Do wzięcia")
                            }
                        }
                    }

                    val notes = s.notes.orEmpty()
                    AnimatedVisibility(visible = notes.isNotBlank()) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // bezpieczna przestrzeń na FAB
        item { Spacer(Modifier.height(100.dp)) }
    }
}
