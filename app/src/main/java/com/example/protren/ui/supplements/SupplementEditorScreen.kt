@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.supplements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.model.SupplementCatalogItem
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
import com.example.protren.repository.SupplementRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SupplementEditorScreen(
    navController: NavController,
    supplementId: String?
) {
    // ====== LIMITY DŁUGOŚCI (frontend guard) ======
    val MAX_SEARCH = 60
    val MAX_NAME = 60
    val MAX_DOSAGE = 60
    val MAX_NOTES = 500

    fun clampText(input: String, max: Int): String =
        if (input.length <= max) input else input.take(max)

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val outerScroll = rememberScrollState()
    val ctx = LocalContext.current
    val prefs = remember(ctx) { UserPreferences(ctx) }

    var loading by remember { mutableStateOf(false) }
    var initialLoad by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var conflictExisting by remember { mutableStateOf<Supplement?>(null) }

    // ---- fallback lokalny
    val localPresets = listOf(
        "Witamina D3",
        "Magnez",
        "Omega-3",
        "Witamina C",
        "Multiwitamina",
        "Kreatyna",
        "Witamina B12",
        "Żelazo"
    )

    // ---- model na ekran
    data class CatalogUIItem(
        val name: String,
        val dosage: String? = null,
        val notes: String? = null,
        val times: List<String>? = null,
        val daysOfWeek: List<Int>? = null,
        val category: String = "inne"
    )

    var catalog by remember { mutableStateOf<List<CatalogUIItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // formularz
    data class FormState(
        val name: String = "",
        val dosage: String = "",
        val notes: String = "",
        val times: Set<String> = emptySet(),
        val daysOfWeek: Set<Int> = emptySet()
    )

    var form by remember(supplementId) { mutableStateOf(FormState()) }

    val timesOptions = listOf(
        "morning" to "Rano",
        "midday" to "Południe",
        "evening" to "Wieczór",
        "night" to "Noc"
    )
    val daysOptions = listOf(
        1 to "Pn",
        2 to "Wt",
        3 to "Śr",
        4 to "Cz",
        5 to "Pt",
        6 to "Sb",
        0 to "Nd"
    )

    // ---- helpery

    fun normalizeCategory(raw: String?): String {
        if (raw.isNullOrBlank()) return "inne"
        // spacje egzotyczne → zwykła
        var v = raw.replace(Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]"), " ")
            .trim()
            .lowercase()
        // jak zaczyna się od "inne" → zawsze "inne"
        if (v.startsWith("inne")) return "inne"
        // różne warianty
        if (v == "trening/redukcja") return "trening / redukcja"
        if (v == "stawy/skóra") return "stawy / skóra"
        return v
    }

    fun validateForm(): String? =
        if (form.name.isBlank()) "Podaj nazwę suplementu" else null

    fun buildApi(): SupplementApi? {
        val token = prefs.getAccessToken()
        if (token.isNullOrBlank()) {
            scope.launch { snackbar.showSnackbar("Brak tokena – zaloguj się ponownie.") }
            return null
        }
        return ApiClient
            .createWithAuth(
                tokenProvider = { token },
                onUnauthorized = { }
            )
            .create(SupplementApi::class.java)
    }

    fun repo(): SupplementRepository? {
        val api = buildApi() ?: return null
        return SupplementRepository(api)
    }

    // ---- 1) pobierz katalog
    LaunchedEffect(Unit) {
        val api = buildApi()
        if (api != null) {
            try {
                val resp = api.getCatalog()
                if (resp.isSuccessful) {
                    val body: List<SupplementCatalogItem> = resp.body().orEmpty()
                    catalog = body
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
                    // fallback
                    catalog = localPresets.map {
                        CatalogUIItem(name = it, category = "inne")
                    }
                }
            } catch (e: Exception) {
                // fallback
                catalog = localPresets.map {
                    CatalogUIItem(name = it, category = "inne")
                }
            }
        } else {
            catalog = localPresets.map {
                CatalogUIItem(name = it, category = "inne")
            }
        }
    }

    // ---- 2) jeżeli edycja – pobierz istniejący suplement
    LaunchedEffect(supplementId) {
        if (supplementId.isNullOrBlank()) {
            initialLoad = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val r = repo() ?: return@LaunchedEffect
            val res = r.getAll()
            if (res.isSuccessful) {
                res.body().orEmpty()
                    .firstOrNull { it._id == supplementId }
                    ?.let { s ->
                        form = FormState(
                            name = clampText(s.name.orEmpty(), MAX_NAME),
                            dosage = clampText(s.dosage.orEmpty(), MAX_DOSAGE),
                            notes = clampText(s.notes.orEmpty(), MAX_NOTES),
                            times = s.times?.toSet().orEmpty(),
                            daysOfWeek = s.daysOfWeek?.toSet().orEmpty()
                        )
                    }
            } else {
                snackbar.showSnackbar("Błąd pobierania: ${res.code()}")
            }
        } catch (e: Exception) {
            snackbar.showSnackbar(e.localizedMessage ?: "Błąd pobierania")
        } finally {
            loading = false
            initialLoad = false
        }
    }

    fun parseServerMsg(res: retrofit2.Response<*>): String? =
        try {
            val raw = res.errorBody()?.string()
            if (raw.isNullOrBlank()) null
            else JSONObject(raw).optString("msg").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    // ---- ZAPIS
    fun save() {
        scope.launch {
            val err = validateForm()
            if (err != null) {
                snackbar.showSnackbar(err)
                return@launch
            }
            loading = true
            try {
                val r = repo() ?: return@launch

                val body = Supplement(
                    _id = supplementId,
                    name = clampText(form.name.trim(), MAX_NAME),
                    dosage = clampText(form.dosage.trim(), MAX_DOSAGE).ifBlank { null },
                    notes = clampText(form.notes.trim(), MAX_NOTES).ifBlank { null },
                    times = form.times.toList().ifEmpty { null },
                    daysOfWeek = form.daysOfWeek.toList().ifEmpty { null }
                )

                val res =
                    if (supplementId.isNullOrBlank()) r.create(body) else r.update(supplementId, body)

                if (res.isSuccessful) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("supplements_changed", true)
                    snackbar.showSnackbar("Zapisano ✅")
                    navController.popBackStack()
                } else if (res.code() == 409) {
                    val msg = parseServerMsg(res) ?: "Suplement o tej nazwie już istnieje."
                    snackbar.showSnackbar(msg)
                    val list = r.getAll()
                    conflictExisting = if (list.isSuccessful) {
                        val nameTrimmed = form.name.trim().lowercase()
                        list.body().orEmpty()
                            .firstOrNull { it.name?.trim()?.lowercase() == nameTrimmed }
                    } else null
                } else {
                    snackbar.showSnackbar(parseServerMsg(res) ?: "Błąd zapisu: ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.localizedMessage ?: "Błąd zapisu")
            } finally {
                loading = false
            }
        }
    }

    // ---- USUWANIE
    fun remove() {
        if (supplementId.isNullOrBlank()) return
        scope.launch {
            loading = true
            try {
                val r = repo() ?: return@launch
                val res = r.delete(supplementId)
                if (res.isSuccessful) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("supplements_changed", true)
                    snackbar.showSnackbar("Usunięto")
                    navController.popBackStack()
                } else {
                    snackbar.showSnackbar(parseServerMsg(res) ?: "Błąd usuwania: ${res.code()}")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.localizedMessage ?: "Błąd usuwania")
            } finally {
                loading = false
                confirmDelete = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = {
                    Text(
                        if (supplementId.isNullOrBlank()) "Nowy suplement" else "Edytuj suplement",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(
                        enabled = !loading && !initialLoad,
                        onClick = { save() }
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Zapisz")
                    }
                    if (!supplementId.isNullOrBlank()) {
                        IconButton(
                            enabled = !loading && !initialLoad,
                            onClick = { confirmDelete = true }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->

        if (initialLoad) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(outerScroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ====== 1. BAZA (nowa, prosta) ======
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
                            onValueChange = { searchQuery = clampText(it, MAX_SEARCH) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            placeholder = { Text("Szukaj…") },
                            supportingText = {
                                Text(
                                    "${searchQuery.length}/$MAX_SEARCH",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // kategorie do chipów
                        val categories = remember(catalog) {
                            catalog
                                .map { normalizeCategory(it.category) }
                                .distinct()
                                .sorted()
                        }

                        // chipy
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
                                    onClick = {
                                        selectedCategory = if (selected) null else cat
                                    },
                                    label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                                    colors = if (selected)
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    else AssistChipDefaults.assistChipColors()
                                )
                            }
                        }

                        // filtrujemy dane
                        val filtered = remember(catalog, searchQuery, selectedCategory) {
                            val q = searchQuery.trim().lowercase()
                            catalog.filter { item ->
                                val matchName = if (q.isBlank()) true else item.name.lowercase().contains(q)
                                val matchCat = if (selectedCategory == null) true
                                else normalizeCategory(item.category) == selectedCategory
                                matchName && matchCat
                            }
                        }

                        // lista płaska
                        if (filtered.isEmpty()) {
                            Text(
                                "Brak wyników.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // używamy LazyColumn wewnątrz karty – z fixed height
                            LazyColumn(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered) { item ->
                                    OutlinedCard(
                                        onClick = {
                                            form = form.copy(
                                                name = clampText(item.name, MAX_NAME),
                                                dosage = clampText(item.dosage.orEmpty(), MAX_DOSAGE),
                                                notes = clampText(item.notes.orEmpty(), MAX_NOTES),
                                                times = item.times?.toSet() ?: emptySet(),
                                                daysOfWeek = item.daysOfWeek?.toSet() ?: emptySet()
                                            )
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
                                            val cat = normalizeCategory(item.category)
                                            Text(
                                                cat.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            "Możesz też wpisać własną nazwę niżej – nadpisze wybór.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ====== 2. DANE SUPLEMENTU ======
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Dane suplementu", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { form = form.copy(name = clampText(it, MAX_NAME)) },
                            label = { Text("Nazwa suplementu") },
                            singleLine = true,
                            isError = validateForm() != null,
                            supportingText = {
                                Column {
                                    AnimatedVisibility(
                                        visible = validateForm() != null,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Text(validateForm() ?: "", color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(
                                        "${form.name.length}/$MAX_NAME",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = form.dosage,
                            onValueChange = { form = form.copy(dosage = clampText(it, MAX_DOSAGE)) },
                            label = { Text("Dawkowanie (np. 2000 IU, 1 kaps.)") },
                            singleLine = true,
                            supportingText = {
                                Text(
                                    "${form.dosage.length}/$MAX_DOSAGE",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = form.notes,
                            onValueChange = { form = form.copy(notes = clampText(it, MAX_NOTES)) },
                            label = { Text("Notatki (opcjonalnie)") },
                            minLines = 2,
                            supportingText = {
                                Text(
                                    "${form.notes.length}/$MAX_NOTES",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ====== 3. PORY DNIA ======
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
                            timesOptions.forEach { (code, label) ->
                                val selected = form.times.contains(code)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        form = if (selected)
                                            form.copy(times = form.times - code)
                                        else
                                            form.copy(times = form.times + code)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // ====== 4. DNI TYGODNIA ======
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
                            daysOptions.forEach { (num, label) ->
                                val selected = form.daysOfWeek.contains(num)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        form = if (selected)
                                            form.copy(daysOfWeek = form.daysOfWeek - num)
                                        else
                                            form.copy(daysOfWeek = form.daysOfWeek + num)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // ====== PRZYCISKI DÓŁ ======
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Anuluj") }

                    Button(
                        onClick = { save() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Zapisz")
                    }
                }
            }
        }
    }

    // ====== dialog usuwania ======
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć suplement?") },
            text = { Text("Tej operacji nie będzie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = { remove() }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
            }
        )
    }

    // ====== dialog konfliktu ======
    conflictExisting?.let { existing ->
        AlertDialog(
            onDismissRequest = { conflictExisting = null },
            title = { Text("Suplement już istnieje") },
            text = { Text("Masz już suplement „${existing.name}”. Zaktualizować go Twoimi danymi?") },
            confirmButton = {
                TextButton(onClick = {
                    conflictExisting = null
                    scope.launch {
                        loading = true
                        try {
                            val r = repo() ?: return@launch
                            val res = r.update(
                                existing._id ?: return@launch,
                                Supplement(
                                    _id = existing._id,
                                    name = clampText(form.name.trim(), MAX_NAME),
                                    dosage = clampText(form.dosage.trim(), MAX_DOSAGE).ifBlank { null },
                                    notes = clampText(form.notes.trim(), MAX_NOTES).ifBlank { null },
                                    times = form.times.toList().ifEmpty { null },
                                    daysOfWeek = form.daysOfWeek.toList().ifEmpty { null }
                                )
                            )
                            if (res.isSuccessful) {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("supplements_changed", true)
                                snackbar.showSnackbar("Zaktualizowano ✅")
                                navController.popBackStack()
                            } else {
                                snackbar.showSnackbar(parseServerMsg(res) ?: "Błąd zapisu")
                            }
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.localizedMessage ?: "Błąd zapisu")
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Zaktualizuj") }
            },
            dismissButton = {
                TextButton(onClick = { conflictExisting = null }) { Text("Anuluj") }
            }
        )
    }
}
