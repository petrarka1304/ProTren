@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.trainer

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.protren.network.TraineeItem
import com.example.protren.viewmodel.TrainerPlanItem
import com.example.protren.viewmodel.TrainerPlansViewModel
import kotlinx.coroutines.launch

@Composable
fun TrainerPlansScreen(nav: NavHostController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val app = LocalContext.current.applicationContext as Application
    val vm: TrainerPlansViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainerPlansViewModel(app) as T
    })

    val plans by vm.plans.collectAsState()
    val trainees by vm.trainees.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showAssign by remember { mutableStateOf(false) }
    var planIdToAssign by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PlanFilter.ALL) }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel trenera") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(visible = !loading, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nowy plan")
                }
            }
        }
    ) { padding ->

        val filteredPlans = remember(plans, search, filter) {
            plans.filter { plan ->
                val textOk = if (search.isBlank()) {
                    true
                } else {
                    plan.name.contains(search, ignoreCase = true)
                }

                val filterOk = when (filter) {
                    PlanFilter.ALL -> true
                    // Szablony = faktyczne plany z dniami
                    PlanFilter.TEMPLATES -> plan.daysCount > 0
                    // Szkice = plany bez dni
                    PlanFilter.DRAFTS -> plan.daysCount == 0
                }

                textOk && filterOk
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> LoadingState()
                error != null -> ErrorState(
                    message = error ?: "",
                    onRetry = { vm.load() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                plans.isEmpty() -> EmptyState(
                    onAdd = { showCreate = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrainerHeader(
                            plans = plans,
                            onAdd = { showCreate = true }
                        )

                        SearchAndFilters(
                            search = search,
                            onSearchChange = { search = it },
                            filter = filter,
                            onFilterChange = { filter = it }
                        )

                        Box(
                            modifier = Modifier.weight(1f, fill = true)
                        ) {
                            PlansList(
                                items = filteredPlans,
                                onOpen = { id -> nav.navigate("trainerPlanEditor/$id") },
                                onEdit = { id -> nav.navigate("trainerPlanEditor/$id") },
                                onDelete = { id ->
                                    vm.delete(id) { _, msg ->
                                        scope.launch { snackbar.showSnackbar(msg) }
                                    }
                                },
                                onAssign = { id ->
                                    // === WAŻNE: tylko pełne plany można przypisać użytkownikowi ===
                                    val plan = plans.firstOrNull { it.id == id }
                                    if (plan == null || plan.daysCount <= 0) {
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                "Nie można przypisać pustego planu. Dodaj najpierw dni i ćwiczenia."
                                            )
                                        }
                                    } else {
                                        planIdToAssign = id
                                        vm.loadTrainees()
                                        showAssign = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showCreate) {
                CreatePlanDialog(
                    onDismiss = { showCreate = false },
                    onCreate = { name ->
                        showCreate = false
                        vm.create(name) { _, msg ->
                            scope.launch { snackbar.showSnackbar(msg) }
                        }
                    }
                )
            }

            if (showAssign) {
                AssignPlanDialogWithList(
                    trainees = trainees,
                    onDismiss = {
                        showAssign = false
                        planIdToAssign = null
                    },
                    onAssign = { clientId ->
                        val pid = planIdToAssign
                        if (pid != null) {
                            showAssign = false
                            vm.assignPlanToClient(pid, clientId) { ok, msg ->
                                scope.launch { snackbar.showSnackbar(msg) }
                            }
                        } else {
                            showAssign = false
                        }
                    }
                )
            }
        }
    }
}

private enum class PlanFilter {
    ALL, TEMPLATES, DRAFTS
}

@Composable
private fun TrainerHeader(
    plans: List<TrainerPlanItem>,
    onAdd: () -> Unit
) {
    val total = plans.size
    val withDays = plans.count { it.daysCount > 0 }
    val drafts = total - withDays

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Panel trenera", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Zarządzaj swoimi szablonami treningów",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = onAdd,
                label = { Text("Nowy plan") },
                leadingIcon = { Icon(Icons.Filled.Add, null) }
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallStatCard(
                title = "Wszystkie",
                value = total.toString(),
                subtitle = "utworzone",
                modifier = Modifier.weight(1f)
            )
            SmallStatCard(
                title = "Gotowe",
                value = withDays.toString(),
                subtitle = "mają dni",
                modifier = Modifier.weight(1f)
            )
            SmallStatCard(
                title = "Szkice",
                value = drafts.toString(),
                subtitle = "bez dni",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SmallStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchAndFilters(
    search: String,
    onSearchChange: (String) -> Unit,
    filter: PlanFilter,
    onFilterChange: (PlanFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Szukaj po nazwie…") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == PlanFilter.ALL,
                onClick = { onFilterChange(PlanFilter.ALL) },
                label = { Text("Wszystkie") }
            )
            FilterChip(
                selected = filter == PlanFilter.TEMPLATES,
                onClick = { onFilterChange(PlanFilter.TEMPLATES) },
                label = { Text("Szablony") }
            )
            FilterChip(
                selected = filter == PlanFilter.DRAFTS,
                onClick = { onFilterChange(PlanFilter.DRAFTS) },
                label = { Text("Szkice") }
            )
        }
    }
}

@Composable
private fun PlansList(
    items: List<TrainerPlanItem>,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAssign: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                "Moje plany",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(items, key = { it.id }) { p ->
            PlanCard(
                name = p.name,
                daysCount = p.daysCount,
                onOpen = { onOpen(p.id) },
                onEdit = { onEdit(p.id) },
                onAssign = { onAssign(p.id) },
                onDelete = { onDelete(p.id) }
            )
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    daysCount: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (daysCount > 0) daysCount.toString() else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        daysCount == 0 -> "Brak dni — szkic (nie można przypisać)"
                        daysCount == 1 -> "1 dzień w planie — gotowy do przypisania"
                        else -> "$daysCount dni w planie — gotowy do przypisania"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edytuj")
            }
            IconButton(
                onClick = onAssign,
                enabled = daysCount > 0 // blokada przypisywania pustych planów
            ) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Przypisz podopiecznemu"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Usuń")
            }
        }
    }
}

@Composable
private fun CreatePlanDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("Nowy plan") }
    var err by remember { mutableStateOf<String?>(null) }

    fun valid(): Boolean {
        err = if (name.isBlank()) "Podaj nazwę planu" else null
        return err == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy plan trenera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa planu") },
                    isError = err != null,
                    supportingText = {
                        if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true
                )
                Text(
                    "Dni, ćwiczenia i przypisania dodasz później.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid()) onCreate(name) }) {
                Text("Utwórz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
fun AssignPlanDialogWithList(
    trainees: List<TraineeItem>,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz podopiecznego") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (trainees.isEmpty()) {
                    Text("Brak przypisanych podopiecznych. Dodaj użytkowników w panelu trenera.")
                } else {
                    Text(
                        "Kliknij na wybranego podopiecznego, aby przypisać mu ten plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                trainees.forEach { t ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAssign(t.userId) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.email, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Coś poszło nie tak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    message.ifBlank { "Spróbuj ponownie za chwilę." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        TextButton(onClick = onRetry) { Text("Odśwież") }
    }
}

@Composable
private fun EmptyState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedCard(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Moje plany (w przygotowaniu)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nie masz jeszcze żadnego szablonu.\nDodaj pierwszy i przypisz go podopiecznym.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("Dodaj pierwszy plan") }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
