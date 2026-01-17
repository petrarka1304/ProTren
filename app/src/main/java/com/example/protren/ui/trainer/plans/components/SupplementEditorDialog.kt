package com.example.protren.ui.trainer.plans.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.protren.model.Supplement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementEditorDialog(
    initial: Supplement?,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        dosage: String?,
        notes: String?,
        times: List<String>,
        daysOfWeek: List<Int>
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var dosage by remember(initial) { mutableStateOf(initial?.dosage ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var times by remember(initial) { mutableStateOf(initial?.times ?: emptyList()) }
    var days by remember(initial) { mutableStateOf(initial?.daysOfWeek ?: emptyList()) }

    val allTimes = listOf("morning", "midday", "evening", "night")
    val allDays = listOf(1,2,3,4,5,6,0) // Pn..Sb,Nd

    val canSave = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onSubmit(name.trim(), dosage.trim(), notes.trim(), times, days.sorted())
            }) { Text(if (initial == null) "Dodaj" else "Zapisz") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Anuluj") } },
        title = { Text(if (initial == null) "Dodaj suplement" else "Edytuj suplement") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dawka") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notatki") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Pory dnia", style = MaterialTheme.typography.labelLarge)
                WrapRow {
                    allTimes.forEach { t ->
                        val selected = times.contains(t)
                        AssistChip(
                            onClick = {
                                times = if (selected) times - t else times + t
                            },
                            label = { Text(timeLabel(t)) },
                            leadingIcon = { if (selected) Icon(Icons.Default.Check, null) }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Text("Dni tygodnia", style = MaterialTheme.typography.labelLarge)
                WrapRow {
                    allDays.forEach { d ->
                        val selected = days.contains(d)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                days = if (selected) days - d else days + d
                            },
                            label = { Text(weekdayLabel(d)) }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    )
}

@Composable
private fun WrapRow(content: @Composable RowScope.() -> Unit) {
    // prościutka „zawijana” linia bez dodatkowych bibliotek
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, content = content)
    }
}

private fun timeLabel(k: String) = when (k) {
    "morning" -> "Rano"
    "midday" -> "Południe"
    "evening" -> "Wieczór"
    "night" -> "Noc"
    else -> k
}

private fun weekdayLabel(n: Int) = when (n) {
    1 -> "Pn"; 2 -> "Wt"; 3 -> "Śr"; 4 -> "Cz"; 5 -> "Pt"; 6 -> "Sb"; 0 -> "Nd"
    else -> n.toString()
}
