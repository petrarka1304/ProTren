@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.protren.ui.exercises

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.CreateExerciseRequest
import com.example.protren.network.ExerciseApi
import com.example.protren.network.ExerciseDto
import com.example.protren.network.ExercisePageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_QUERY_LEN = 60
private const val MAX_EXERCISE_NAME_LEN = 60
private const val MAX_GROUP_LEN = 30
private const val MAX_EQUIPMENT_LEN = 30

private fun sanitizeSingleLine(input: String, maxLen: Int): String {
    val oneLine = input
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("\t", " ")
    val collapsed = oneLine.replace(Regex("\\s+"), " ")
    return collapsed.take(maxLen)
}

private data class CategoryUi(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun normalizeGroupKey(raw: String?): String {
    val s = (raw ?: "").trim().lowercase()
    if (s.isBlank()) return "inne"

    return when {
        s.contains("klat") || s.contains("chest") -> "klatka"
        s.contains("plec") || s.contains("back") -> "plecy"
        s.contains("bark") || s.contains("shoulder") -> "barki"
        s.contains("ręc") || s.contains("rece") || s.contains("arm") || s.contains("biceps") || s.contains("triceps") -> "rece"
        s.contains("nog") || s.contains("leg") || s.contains("uda") || s.contains("poślad") || s.contains("poslad") -> "nogi"
        s.contains("core") || s.contains("brzuch") || s.contains("abs") -> "core"
        s.contains("full") || s.contains("całe") || s.contains("cale") -> "cale_cialo"
        else -> "inne"
    }
}

private fun categoryUiForKey(key: String): CategoryUi {
    return when (key) {
        "klatka" -> CategoryUi("klatka", "Klatka", Icons.Filled.Favorite)
        "plecy" -> CategoryUi("plecy", "Plecy", Icons.Filled.ViewStream)
        "barki" -> CategoryUi("barki", "Barki", Icons.Filled.AccessibilityNew)
        "rece" -> CategoryUi("rece", "Ręce", Icons.Filled.SportsMma)
        "nogi" -> CategoryUi("nogi", "Nogi", Icons.Filled.DirectionsRun)
        "core" -> CategoryUi("core", "Core", Icons.Filled.CenterFocusStrong)
        "cale_cialo" -> CategoryUi("cale_cialo", "Całe ciało", Icons.Filled.FitnessCenter)
        else -> CategoryUi("inne", "Inne", Icons.Filled.Category)
    }
}

private data class BodyPartUi(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun bodyPartKeyForGroupKey(groupKey: String): String = when (groupKey) {
    "klatka", "plecy", "barki", "rece" -> "gora"
    "nogi" -> "dol"
    "core" -> "core"
    "cale_cialo" -> "cale"
    else -> "inne"
}

private fun bodyPartsUi(): List<BodyPartUi> = listOf(
    BodyPartUi("all", "Całe ciało (filtr off)", Icons.Filled.AllInclusive),
    BodyPartUi("gora", "Góra", Icons.Filled.ExpandLess),
    BodyPartUi("dol", "Dół", Icons.Filled.ExpandMore),
    BodyPartUi("core", "Core", Icons.Filled.CenterFocusStrong),
    BodyPartUi("cale", "Full body", Icons.Filled.FitnessCenter),
    BodyPartUi("inne", "Inne", Icons.Filled.Category)
)

data class ExerciseUi(
    val id: String,
    val name: String,
    val group: String,
    val equipment: String? = null,
    val isCustom: Boolean = false
)

const val EXERCISE_PICKER_RESULT_IDS = "picked_exercise_ids"
const val EXERCISE_PICKER_RESULT_NAMES = "picked_exercise_names"
const val EXERCISE_PICKER_PRESELECTED_IDS = "picker_preselected_ids"

private fun ExerciseDto.toUi() = ExerciseUi(
    id = _id,
    name = name,
    group = (group ?: "Inne").ifBlank { "Inne" },
    equipment = equipment?.takeIf { it.isNotBlank() },
    isCustom = false
)

@Composable
fun ExercisePickerScreen(navController: NavController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val data = remember { mutableStateListOf<ExerciseUi>() }

    val previousEntry = navController.previousBackStackEntry
    val initialPreselected: List<String> = remember(previousEntry) {
        previousEntry
            ?.savedStateHandle
            ?.get<ArrayList<String>>(EXERCISE_PICKER_PRESELECTED_IDS)
            ?: arrayListOf()
    }
    val selected = remember { mutableStateListOf<String>() }

    val selectedNames = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(initialPreselected) {
        if (initialPreselected.isNotEmpty()) {
            selected.clear()
            selected.addAll(initialPreselected)
            previousEntry?.savedStateHandle?.remove<ArrayList<String>>(EXERCISE_PICKER_PRESELECTED_IDS)
        }
    }

    LaunchedEffect(data.size, selected.size) {
        data.filter { it.id in selected }.forEach { ex ->
            selectedNames[ex.id] = ex.name
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryKey by rememberSaveable { mutableStateOf("") }
    var selectedBodyPartKey by rememberSaveable { mutableStateOf("all") }
    var selectedEquipment by rememberSaveable { mutableStateOf("Sprzęt: wszystkie") }
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        reload(
            ctx = ctx,
            page = 1,
            query = null,
            group = null,
            equipment = null,
            mine = null,
            onLoading = { isLoading = true; loadError = null },
            onSuccess = { items, end ->
                isLoading = false
                endReached = end
                data.clear()
                data.addAll(items)
            },
            onError = { msg ->
                isLoading = false
                loadError = msg
                scope.launch { snackbar.showSnackbar(msg) }
            }
        )
    }

    val bodyParts = remember { bodyPartsUi() }

    val equipmentOptions = remember {
        listOf(
            "Sprzęt: wszystkie",
            "Siłownia", "Dom", "Siłownia/Dom",
            "Drążek", "Brak",
            "Maszyna", "Linki",
            "Sztanga", "Hantle", "Kettlebell"
        )
    }

    val filtered by remember(data, query, selectedCategoryKey, selectedBodyPartKey, selectedEquipment, mineOnly) {
        derivedStateOf {
            val q = query.trim().lowercase()
            val eq = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" }

            data.asSequence()
                .filter { ex ->
                    val gKey = normalizeGroupKey(ex.group)

                    val okCategory =
                        selectedCategoryKey.isBlank() || gKey == selectedCategoryKey

                    val okBodyPart =
                        selectedBodyPartKey == "all" ||
                                bodyPartKeyForGroupKey(gKey) == selectedBodyPartKey

                    okCategory && okBodyPart
                }
                .filter { q.isBlank() || it.name.lowercase().contains(q) }
                .filter { eq == null || (it.equipment ?: "").contains(eq, ignoreCase = true) }
                .toList()
        }
    }

    val hasChanges by remember(initialPreselected) {
        derivedStateOf { selected.toSet() != initialPreselected.toSet() }
    }

    fun finishAndReturn() {
        val ids = java.util.ArrayList(selected)

        val names = java.util.ArrayList(
            selected.map { id ->
                selectedNames[id]
                    ?: data.firstOrNull { it.id == id }?.name
                    ?: id
            }
        )

        val backEntry = navController.previousBackStackEntry ?: navController.currentBackStackEntry
        backEntry?.savedStateHandle?.apply {
            set(EXERCISE_PICKER_RESULT_IDS, ids)
            set(EXERCISE_PICKER_RESULT_NAMES, names)
        }
        navController.popBackStack()
    }

    fun handleBack() {
        if (hasChanges) showUnsavedDialog = true else navController.popBackStack()
    }

    BackHandler {
        if (showUnsavedDialog) showUnsavedDialog = false else handleBack()
    }

    fun triggerSearch() {
        currentPage = 1
        endReached = false

        val safeQuery = query.trim()
            .takeIf { it.isNotBlank() }
            ?.let { sanitizeSingleLine(it, MAX_QUERY_LEN) }

        scope.launch {
            isLoading = true
            loadError = null

            try {
                if (safeQuery.isNullOrBlank()) {
                    reload(
                        ctx = ctx,
                        page = 1,
                        query = null,
                        group = null,
                        equipment = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" },
                        mine = mineOnly,
                        onLoading = { /* already loading */ },
                        onSuccess = { items, end ->
                            isLoading = false
                            endReached = end
                            data.clear()
                            data.addAll(items)
                        },
                        onError = { msg ->
                            isLoading = false
                            loadError = msg
                            scope.launch { snackbar.showSnackbar(msg) }
                        }
                    )
                } else {
                    val fromBackend = fetchAllExercisesByQuery(
                        ctx = ctx,
                        query = safeQuery,
                        equipment = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" },
                        mine = mineOnly
                    )

                    val anyMatch = fromBackend.any { it.name.contains(safeQuery, ignoreCase = true) }

                    val finalList = if (anyMatch) {
                        fromBackend
                    } else {
                        fetchAllExercises(
                            ctx = ctx,
                            equipment = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" },
                            mine = mineOnly
                        )
                    }

                    data.clear()
                    data.addAll(finalList)

                    endReached = true
                    currentPage = 1
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
                loadError = e.message ?: "Błąd wyszukiwania"
                snackbar.showSnackbar(loadError!!)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Wybierz ćwiczenia") },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Dodaj własne")
                    }
                    TextButton(onClick = { finishAndReturn() }) { Text("GOTOWE") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            loadError != null -> {
                ErrorView(msg = loadError!!, onRetry = { triggerSearch() })
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { input -> query = sanitizeSingleLine(input, MAX_QUERY_LEN) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            placeholder = { Text("Szukaj ćwiczenia ") },
                            supportingText = { Text("${query.length}/$MAX_QUERY_LEN") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { triggerSearch() }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { triggerSearch() }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Szukaj")
                                }
                            }
                        )
                    }

                    item {
                        Text(
                            "Część ciała",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                                .padding(vertical = 10.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                items(bodyParts, key = { it.key }) { bp ->
                                    val selectedNow = selectedBodyPartKey == bp.key
                                    FilterChip(
                                        selected = selectedNow,
                                        onClick = { selectedBodyPartKey = bp.key },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(bp.icon, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    bp.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(equipmentOptions) { opt ->
                                    FilterChip(
                                        selected = selectedEquipment == opt,
                                        onClick = {
                                            selectedEquipment = opt
                                            if (query.trim().isNotBlank()) triggerSearch()
                                            else {
                                                currentPage = 1
                                                endReached = false
                                                scope.launch {
                                                    reload(
                                                        ctx = ctx,
                                                        page = 1,
                                                        query = null,
                                                        group = null,
                                                        equipment = opt.takeIf { it != "Sprzęt: wszystkie" },
                                                        mine = mineOnly,
                                                        onLoading = { isLoading = true },
                                                        onSuccess = { items, end ->
                                                            isLoading = false
                                                            endReached = end
                                                            data.clear()
                                                            data.addAll(items)
                                                        },
                                                        onError = { msg ->
                                                            isLoading = false
                                                            scope.launch { snackbar.showSnackbar(msg) }
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tylko moje")
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = mineOnly,
                                    onCheckedChange = { checked ->
                                        mineOnly = checked
                                        if (query.trim().isNotBlank()) triggerSearch()
                                        else {
                                            currentPage = 1
                                            endReached = false
                                            scope.launch {
                                                reload(
                                                    ctx = ctx,
                                                    page = 1,
                                                    query = null,
                                                    group = null,
                                                    equipment = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" },
                                                    mine = checked,
                                                    onLoading = { isLoading = true },
                                                    onSuccess = { items, end ->
                                                        isLoading = false
                                                        endReached = end
                                                        data.clear()
                                                        data.addAll(items)
                                                    },
                                                    onError = { msg ->
                                                        isLoading = false
                                                        scope.launch { snackbar.showSnackbar(msg) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("Brak wyników. Zmień filtry lub dodaj własne ćwiczenie.") }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ex ->
                            val isSelected = ex.id in selected
                            ExerciseRow(
                                item = ex.copy(
                                    group = categoryUiForKey(normalizeGroupKey(ex.group)).label
                                ),
                                selected = isSelected,
                                onToggle = {
                                    if (isSelected) {
                                        selected.remove(ex.id)
                                        selectedNames.remove(ex.id)
                                    } else {
                                        selected.add(ex.id)
                                        selectedNames[ex.id] = ex.name
                                        scope.launch { snackbar.showSnackbar("Dodano: ${ex.name}") }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        item {
                            if (!endReached && query.trim().isBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            reload(
                                                ctx = ctx,
                                                page = currentPage + 1,
                                                query = null,
                                                group = null,
                                                equipment = selectedEquipment.takeIf { it != "Sprzęt: wszystkie" },
                                                mine = mineOnly,
                                                onLoading = { isLoading = true },
                                                onSuccess = { items, end ->
                                                    isLoading = false
                                                    endReached = end
                                                    data.addAll(items)
                                                    currentPage += 1
                                                },
                                                onError = { msg ->
                                                    isLoading = false
                                                    scope.launch { snackbar.showSnackbar(msg) }
                                                }
                                            )
                                        }
                                    }) { Text("Załaduj więcej") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddCustomExerciseSheet(
            presetGroup = selectedCategoryKey.takeIf { it != "all" }?.let { categoryUiForKey(it).label },
            onDismiss = { showAddSheet = false },
            onSave = { name, group, equipment ->
                scope.launch {
                    val trimmedName = sanitizeSingleLine(name.trim(), MAX_EXERCISE_NAME_LEN)
                    val trimmedGroup = sanitizeSingleLine(group.trim(), MAX_GROUP_LEN).ifBlank { "" }
                    val trimmedEq = sanitizeSingleLine(equipment.trim(), MAX_EQUIPMENT_LEN).ifBlank { "" }

                    if (trimmedName.isBlank()) {
                        snackbar.showSnackbar("Podaj nazwę ćwiczenia.")
                        return@launch
                    }
                    if (trimmedName.length < 2) {
                        snackbar.showSnackbar("Nazwa ćwiczenia jest zbyt krótka.")
                        return@launch
                    }

                    try {
                        val prefs = UserPreferences(ctx)
                        val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""

                        val retrofit = ApiClient.createWithAuth(
                            tokenProvider = { token },
                            onUnauthorized = {
                                scope.launch { snackbar.showSnackbar("Sesja wygasła. Zaloguj się ponownie.") }
                            }
                        )
                        val api = retrofit.create(ExerciseApi::class.java)

                        val res = withContext(Dispatchers.IO) {
                            api.createExercise(
                                CreateExerciseRequest(
                                    name = trimmedName,
                                    group = trimmedGroup.ifBlank { null },
                                    equipment = trimmedEq.ifBlank { null },
                                    tags = emptyList()
                                )
                            )
                        }

                        if (res.isSuccessful) {
                            val dto = res.body()!!
                            val ex = dto.toUi().copy(isCustom = true)
                            data.add(0, ex)
                            selected.add(ex.id)
                            selectedNames[ex.id] = ex.name
                            snackbar.showSnackbar("Dodano własne ćwiczenie: ${ex.name}")
                            showAddSheet = false
                        } else {
                            snackbar.showSnackbar("Nie udało się zapisać ćwiczenia (HTTP ${res.code()})")
                        }
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "Błąd połączenia przy zapisie ćwiczenia")
                    }
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Niezapisane zmiany") },
            text = { Text("Masz niezapisane zmiany w wyborze ćwiczeń. Czy na pewno chcesz wyjść bez zapisania?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    navController.popBackStack()
                }) { Text("Odrzuć zmiany") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun ErrorView(msg: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nie udało się pobrać ćwiczeń.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

@Composable
private fun ExerciseRow(
    item: ExerciseUi,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val sub = buildString {
                    append(item.group)
                    item.equipment?.takeIf { it.isNotBlank() }?.let {
                        append(" • "); append(it)
                    }
                    if (item.isCustom) append(" • własne")
                }
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (selected) {
                FilledTonalButton(onClick = onToggle) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dodano")
                }
            } else {
                OutlinedButton(onClick = onToggle) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dodaj")
                }
            }
        }
    }
}

@Composable
private fun AddCustomExerciseSheet(
    presetGroup: String? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, group: String, equipment: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(presetGroup ?: "") }
    var equipment by rememberSaveable { mutableStateOf("") }

    val groups = listOf("Klatka", "Plecy", "Barki", "Ręce", "Nogi", "Core", "Całe ciało", "Inne")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nowe ćwiczenie", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { input -> name = sanitizeSingleLine(input, MAX_EXERCISE_NAME_LEN) },
                label = { Text("Nazwa ćwiczenia *") },
                supportingText = { Text("${name.length}/$MAX_EXERCISE_NAME_LEN") },
                singleLine = true
            )

            Text("Grupa mięśniowa", style = MaterialTheme.typography.labelSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { g ->
                    FilterChip(
                        selected = group.equals(g, true),
                        onClick = { group = sanitizeSingleLine(g, MAX_GROUP_LEN) },
                        label = { Text(g) }
                    )
                }
            }

            OutlinedTextField(
                value = group,
                onValueChange = { input -> group = sanitizeSingleLine(input, MAX_GROUP_LEN) },
                label = { Text("Lub wpisz własną grupę") },
                supportingText = { Text("${group.length}/$MAX_GROUP_LEN") },
                singleLine = true
            )

            OutlinedTextField(
                value = equipment,
                onValueChange = { input -> equipment = sanitizeSingleLine(input, MAX_EQUIPMENT_LEN) },
                label = { Text("Sprzęt (opcjonalnie)") },
                supportingText = { Text("${equipment.length}/$MAX_EQUIPMENT_LEN") },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Anuluj") }
                Button(
                    onClick = { onSave(name, group, equipment) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Zapisz") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private suspend fun reload(
    ctx: Context,
    page: Int,
    query: String?,
    group: String?,
    equipment: String?,
    mine: Boolean?,
    onLoading: () -> Unit,
    onSuccess: (items: List<ExerciseUi>, end: Boolean) -> Unit,
    onError: (String) -> Unit
) {
    onLoading()
    try {
        val prefs = UserPreferences(ctx)
        val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""

        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { token },
            onUnauthorized = { }
        )
        val api = retrofit.create(ExerciseApi::class.java)

        val res = withContext(Dispatchers.IO) {
            api.getExercises(
                query = query?.let { sanitizeSingleLine(it, MAX_QUERY_LEN) },
                group = group?.let { sanitizeSingleLine(it, MAX_GROUP_LEN) },
                equipment = equipment?.let { sanitizeSingleLine(it, MAX_EQUIPMENT_LEN) },
                page = page,
                limit = 50,
                mine = mine
            )
        }

        if (res.isSuccessful) {
            val body: ExercisePageDto? = res.body()
            val items = body?.items?.map { it.toUi() }.orEmpty()
            val end = items.isEmpty() || items.size < 50
            onSuccess(items, end)
        } else {
            onError("Błąd ${res.code()}")
        }
    } catch (e: Exception) {
        onError(e.message ?: "Błąd połączenia z serwerem")
    }
}

private suspend fun fetchAllExercisesByQuery(
    ctx: Context,
    query: String,
    equipment: String?,
    mine: Boolean?
): List<ExerciseUi> {
    val prefs = UserPreferences(ctx)
    val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""

    val retrofit = ApiClient.createWithAuth(
        tokenProvider = { token },
        onUnauthorized = { }
    )
    val api = retrofit.create(ExerciseApi::class.java)

    val limit = 50
    val maxPagesSafety = 40

    val all = LinkedHashMap<String, ExerciseUi>()
    var page = 1

    while (page <= maxPagesSafety) {
        val res = withContext(Dispatchers.IO) {
            api.getExercises(
                query = sanitizeSingleLine(query, MAX_QUERY_LEN),
                group = null,
                equipment = equipment?.let { sanitizeSingleLine(it, MAX_EQUIPMENT_LEN) },
                page = page,
                limit = limit,
                mine = mine
            )
        }

        if (!res.isSuccessful) {
            throw IllegalStateException("Błąd ${res.code()} podczas wyszukiwania")
        }

        val body = res.body()
        val items = body?.items?.map { it.toUi() }.orEmpty()

        items.forEach { all[it.id] = it }
        if (items.isEmpty() || items.size < limit) break

        page += 1
    }

    return all.values.toList()
}

private suspend fun fetchAllExercises(
    ctx: Context,
    equipment: String?,
    mine: Boolean?
): List<ExerciseUi> {
    val prefs = UserPreferences(ctx)
    val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: ""

    val retrofit = ApiClient.createWithAuth(
        tokenProvider = { token },
        onUnauthorized = { }
    )
    val api = retrofit.create(ExerciseApi::class.java)

    val limit = 50
    val maxPagesSafety = 80

    val all = LinkedHashMap<String, ExerciseUi>()
    var page = 1

    while (page <= maxPagesSafety) {
        val res = withContext(Dispatchers.IO) {
            api.getExercises(
                query = null,
                group = null,
                equipment = equipment?.let { sanitizeSingleLine(it, MAX_EQUIPMENT_LEN) },
                page = page,
                limit = limit,
                mine = mine
            )
        }

        if (!res.isSuccessful) {
            throw IllegalStateException("Błąd ${res.code()} podczas pobierania ćwiczeń")
        }

        val items = res.body()?.items?.map { it.toUi() }.orEmpty()
        items.forEach { all[it.id] = it }

        if (items.isEmpty() || items.size < limit) break
        page += 1
    }

    return all.values.toList()
}
