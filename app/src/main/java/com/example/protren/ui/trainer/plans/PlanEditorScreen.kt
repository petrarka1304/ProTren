package com.example.protren.ui.trainer.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.protren.model.SupplementPlanDto
import com.example.protren.model.SupplementPlanItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    plan: SupplementPlanDto,
    onSavePlan: (name: String, notes: String?, isActive: Boolean?) -> Unit,
    onAddItem: (SupplementPlanItem) -> Unit,
    onUpdateItem: (index: Int, item: SupplementPlanItem) -> Unit,
    onDeleteItem: (index: Int) -> Unit
) {
    var name by remember(plan) { mutableStateOf(TextFieldValue(plan.name)) }
    var notes by remember(plan) { mutableStateOf(TextFieldValue(plan.notes)) }
    var isActive by remember(plan) { mutableStateOf(plan.isActive) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edycja planu") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                onAddItem(SupplementPlanItem(
                    supplementName = "Nowy suplement",
                    dose = "1 kaps.",
                    timing = "rano",
                    frequency = "daily"
                ))
            }) { Icon(Icons.Default.Add, contentDescription = "Dodaj pozycję") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa planu") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notatki") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = isActive, onClick = { isActive = !isActive }, label = { Text(if (isActive) "Aktywny" else "Zakończony") })
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = { onSavePlan(name.text, notes.text, isActive) }) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Zapisz plan")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Pozycje", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(plan.supplements) { index, item ->
                ItemCard(item, onChange = { onUpdateItem(index, it) }, onDelete = { onDeleteItem(index) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCard(
    item: SupplementPlanItem,
    onChange: (SupplementPlanItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(item) { mutableStateOf(TextFieldValue(item.supplementName)) }
    var dose by remember(item) { mutableStateOf(TextFieldValue(item.dose)) }
    var timing by remember(item) { mutableStateOf(TextFieldValue(item.timing)) }
    var frequency by remember(item) { mutableStateOf(item.frequency) }
    var freqExpanded by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = {
                name = it; onChange(item.copy(supplementName = it.text))
            }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dose, onValueChange = {
                dose = it; onChange(item.copy(dose = it.text))
            }, label = { Text("Dawka") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = timing, onValueChange = {
                timing = it; onChange(item.copy(timing = it.text))
            }, label = { Text("Pora") }, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = freqExpanded, onExpandedChange = { freqExpanded = !freqExpanded }) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    value = frequency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Częstotliwość") }
                )
                ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                    listOf("daily", "weekly", "custom").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                frequency = option
                                onChange(item.copy(frequency = option))
                                freqExpanded = false
                            }
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Usuń")
                }
            }
        }
    }
}
