package com.example.protren.ui.trainer.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
import com.example.protren.network.TrainerPlanApi
import com.example.protren.ui.trainer.plans.components.TrainerSupplementEditorSheet

@Composable
fun TrainerPlansScreen(
    traineeId: String,
    api: TrainerPlanApi,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    val supplementApi: SupplementApi = remember {
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            onUnauthorized = { /* możesz wylogować */ }
        )
        retrofit.create(SupplementApi::class.java)
    }

    val vm: TrainerPlansViewModel =
        viewModel(factory = TrainerPlansViewModelFactory(api))

    LaunchedEffect(traineeId) { vm.init(traineeId) }
    val ui by vm.ui.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ui.error) { ui.error?.let { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openCreate() },
                icon = { Icon(Icons.Default.Add, contentDescription = "Dodaj suplement") },
                text = { Text("Dodaj suplement") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {

            when {
                ui.isLoading && ui.items.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                ui.items.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Brak suplementów", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Kliknij „Dodaj suplement”, aby dodać pierwszy.")
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = ui.items,
                            key = { index, it -> it._id ?: "supp-$index-${it.name.orEmpty()}-${it.hashCode()}" }
                        ) { _, item ->
                            SupplementCard(
                                item = item,
                                onEdit = { vm.openEdit(item) },
                                onDelete = { vm.deleteSupplement(item) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    TrainerSupplementEditorSheet(
        visible = ui.editorVisible,
        initial = ui.editorInitial,
        supplementApi = supplementApi,
        onDismiss = { vm.closeEditor() },
        onSubmit = { name, dosage, notes, times, days ->
            vm.saveSupplement(name, dosage, notes, times, days)
        }
    )
}

@Composable
private fun SupplementCard(
    item: Supplement,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name ?: "Bez nazwy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edytuj") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Usuń") }
            }
            item.dosage?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            if (!item.times.isNullOrEmpty()) {
                Text("Pory: ${item.times.joinToString()}", style = MaterialTheme.typography.labelMedium)
            }
            if (!item.daysOfWeek.isNullOrEmpty()) {
                Text("Dni: ${item.daysOfWeek.joinToString()}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
