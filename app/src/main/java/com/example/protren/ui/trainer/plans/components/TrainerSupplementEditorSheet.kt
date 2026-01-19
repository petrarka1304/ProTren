@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.trainer.plans.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.protren.model.Supplement
import com.example.protren.model.SupplementCatalogItem
import com.example.protren.network.SupplementApi
import kotlinx.coroutines.launch

@Composable
fun TrainerSupplementEditorSheet(
    visible: Boolean,
    initial: Supplement?,
    supplementApi: SupplementApi,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        dosage: String?,
        notes: String?,
        times: List<String>,
        daysOfWeek: List<Int>
    ) -> Unit
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    data class CatalogUIItem(
        val name: String,
        val dosage: String? = null,
        val notes: String? = null,
        val times: List<String>? = null,
        val daysOfWeek: List<Int>? = null,
        val category: String = "inne"
    )

    fun normalizeCategory(raw: String?): String {
        if (raw.isNullOrBlank()) return "inne"
        var v = raw.replace(Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]"), " ")
            .trim()
            .lowercase()
        if (v.startsWith("inne")) return "inne"
        if (v == "trening/redukcja") return "trening / redukcja"
        if (v == "stawy/skóra") return "stawy / skóra"
        return v
    }

    var catalog by remember { mutableStateOf<List<CatalogUIItem>>(emptyList()) }
    var loadingCatalog by remember { mutableStateOf(true) }

    val localPresets = listOf(
        "Witamina D3", "Magnez", "Omega-3", "Witamina C",
        "Multiwitamina", "Kreatyna", "Witamina B12", "Żelazo"
    )

    LaunchedEffect(Unit) {
        loadingCatalog = true
        runCatching {
            val resp = supplementApi.getCatalog()
            if (resp.isSuccessful) {
                catalog = resp.body().orEmpty()
                    .filter { it.isActive != false }
                    .map {
                        CatalogUIItem(
                            name = it.name,
                            dosage = it.defaultDosage,
                            notes = it.description,
                            times = it.defaultTimes,
                            daysOfWeek = it.defaultDaysOfWeek,
                            category = normalizeCategory(it.category)
                        )
                    }
            } else {
                catalog = localPresets.map { CatalogUIItem(name = it, category = "inne") }
            }
        }.onFailure {
            catalog = localPresets.map { CatalogUIItem(name = it, category = "inne") }
        }
        loadingCatalog = false
    }

    var name by remember(initial) { mutableStateOf(TextFieldValue(initial?.name ?: "")) }
    var dosage by remember(initial) { mutableStateOf(TextFieldValue(initial?.dosage ?: "")) }
    var notes by remember(initial) { mutableStateOf(TextFieldValue(initial?.notes ?: "")) }
    var times by remember(initial) { mutableStateOf(initial?.times ?: emptyList()) }
    var days by remember(initial) { mutableStateOf(initial?.daysOfWeek ?: emptyList()) }

    val allTimes = listOf("morning", "midday", "evening", "night")
    val daysOptions = listOf(1,2,3,4,5,6,0)
    val canSave = name.text.trim().isNotEmpty()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        windowInsets = WindowInsets(0),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (initial == null) "Dodaj suplement" else "Edytuj suplement",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Wybierz z bazy", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text("Szukaj…") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val categories = remember(catalog) {
                        catalog.map { normalizeCategory(it.category) }.distinct().sorted()
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { selectedCategory = null },
                            label = { Text("Wszystkie") },
                            leadingIcon = if (selectedCategory == null) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null,
                            colors = if (selectedCategory == null)
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            else AssistChipDefaults.assistChipColors()
                        )
                        categories.forEach { cat ->
                            val selected = selectedCategory == cat
                            AssistChip(
                                onClick = { selectedCategory = if (selected) null else cat },
                                label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                                colors = if (selected)
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                else AssistChipDefaults.assistChipColors()
                            )
                        }
                    }

                    if (loadingCatalog) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val q = searchQuery.trim().lowercase()
                        val filtered = catalog.filter { item ->
                            val n = item.name.lowercase().contains(q)
                            val c = selectedCategory?.let { normalizeCategory(item.category) == it } ?: true
                            n && c
                        }

                        if (filtered.isEmpty()) {
                            Text(
                                "Brak wyników.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered) { item ->
                                    OutlinedCard(
                                        onClick = {
                                            name = TextFieldValue(item.name)
                                            dosage = TextFieldValue(item.dosage.orEmpty())
                                            notes = TextFieldValue(item.notes.orEmpty())
                                            times = item.times ?: emptyList()
                                            days = item.daysOfWeek ?: emptyList()
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            val sub = listOfNotNull(
                                                item.dosage,
                                                item.times?.joinToString(", ")
                                            ).joinToString(" • ")
                                            if (sub.isNotBlank()) {
                                                Text(
                                                    sub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                normalizeCategory(item.category)
                                                    .replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            "Możesz też wpisać własną nazwę poniżej – nadpisze wybór.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Dane suplementu", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nazwa suplementu*") },
                        singleLine = true,
                        isError = name.text.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dawkowanie (np. 2000 IU, 1 kaps.)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notatki (opcjonalnie)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Pory dnia", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTimes.forEach { t ->
                            val selected = t in times
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    times = if (selected) times - t else times + t
                                },
                                label = {
                                    Text(
                                        when (t) {
                                            "morning" -> "Rano"
                                            "midday" -> "Południe"
                                            "evening" -> "Wieczór"
                                            "night" -> "Noc"
                                            else -> t
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Dni tygodnia", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        daysOptions.forEach { d ->
                            val selected = d in days
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    days = if (selected) days - d else (days + d).sorted()
                                },
                                label = {
                                    Text(
                                        when (d) {
                                            1 -> "Pn"; 2 -> "Wt"; 3 -> "Śr"; 4 -> "Cz"
                                            5 -> "Pt"; 6 -> "Sb"; 0 -> "Nd"
                                            else -> d.toString()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Anuluj") }

                Button(
                    onClick = {
                        if (!canSave) {
                            scope.launch { snackbar.showSnackbar("Podaj nazwę suplementu") }
                            return@Button
                        }
                        onSubmit(
                            name.text.trim(),
                            dosage.text.trim().ifEmpty { "" },
                            notes.text.trim().ifEmpty { "" },
                            times,
                            days
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) { Text(if (initial == null) "Dodaj" else "Zapisz") }
            }
        }
    }
}
