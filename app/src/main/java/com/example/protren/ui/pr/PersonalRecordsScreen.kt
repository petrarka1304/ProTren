@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.pr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class SortBy {
    MAX_WEIGHT,
    ONE_RM,
    VOLUME,
    MAX_REPS
}

private data class PRItem(
    val name: String,
    val maxWeight: Int,
    val bestRepsAtMaxWeight: Int,
    val oneRm: Int,
    val maxVolume: Int,
    val lastDate: String?
)

@Composable
fun PersonalRecordsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var allLogs by remember { mutableStateOf<List<WorkoutLog>>(emptyList()) }

    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(SortBy.MAX_WEIGHT) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val token = prefs.getAccessToken()
                if (token.isNullOrBlank()) {
                    error = "Sesja wygasła. Zaloguj się ponownie."
                    return@launch
                }

                val api = ApiClient.createWithAuth({ token }).create(WorkoutApi::class.java)
                val res = api.getWorkoutLogs(null, null)

                if (res.isSuccessful) {
                    allLogs = res.body().orEmpty()
                } else {
                    error = when (res.code()) {
                        401, 403 -> "Sesja wygasła. Zaloguj się ponownie."
                        else -> "Błąd serwera (HTTP ${res.code()})."
                    }
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Nieznany błąd."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val prs = remember(allLogs) { computePRs(allLogs) }

    val filtered = remember(prs, query, sortBy) {
        val q = query.trim()
        prs.asSequence()
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .sortedWith(
                when (sortBy) {
                    SortBy.MAX_WEIGHT -> compareByDescending<PRItem> { it.maxWeight }
                    SortBy.ONE_RM -> compareByDescending { it.oneRm }
                    SortBy.VOLUME -> compareByDescending { it.maxVolume }
                    SortBy.MAX_REPS -> compareByDescending { it.bestRepsAtMaxWeight }
                }
            )
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rekordy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!loading && error == null && filtered.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                filtered.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            error != null -> KompaktowyStanBledu(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                komunikat = error!!,
                onRetry = { load() }
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Szukaj ćwiczenia") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Wyczyść")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SortDropdown(
                        current = sortBy,
                        onPick = { sortBy = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filtered.isEmpty()) {
                    KompaktowyStanPustyWynikow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        czyJestZapytanie = query.isNotBlank(),
                        onClear = { query = "" }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 92.dp, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.name }) { pr ->
                            KartaRekordu(
                                pr = pr,
                                onOpen = {
                                    val encoded = URLEncoder.encode(
                                        pr.name,
                                        StandardCharsets.UTF_8.name()
                                    )
                                    navController.navigate("pr/$encoded")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun KompaktowyStanBledu(
    modifier: Modifier = Modifier,
    komunikat: String,
    onRetry: () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                komunikat,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

@Composable
private fun KompaktowyStanPustyWynikow(
    modifier: Modifier = Modifier,
    czyJestZapytanie: Boolean,
    onClear: () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (czyJestZapytanie) "Brak wyników" else "Brak rekordów do wyświetlenia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (czyJestZapytanie) "Spróbuj wpisać inną nazwę ćwiczenia."
                else "Dodaj treningi, a tutaj pokażą się Twoje najlepsze wyniki.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (czyJestZapytanie) {
                OutlinedButton(onClick = onClear) { Text("Wyczyść wyszukiwanie") }
            }
        }
    }
}


@Composable
private fun KartaRekordu(
    pr: PRItem,
    onOpen: () -> Unit
) {
    ElevatedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pr.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    pr.lastDate?.let {
                        Text(
                            text = "Ostatnio: $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Szczegóły",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniPigułka("Maks", "${pr.maxWeight} kg")
                MiniPigułka("1RM", "${pr.oneRm} kg")
                MiniPigułka("Objętość", "${pr.maxVolume}")
                }
        }
    }
}

@Composable
private fun MiniPigułka(nazwa: String, wartosc: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                nazwa,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                wartosc,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
private fun SortDropdown(
    current: SortBy,
    onPick: (SortBy) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    fun label(s: SortBy) = when (s) {
        SortBy.MAX_WEIGHT -> "Maksymalny ciężar"
        SortBy.ONE_RM -> "1RM (szacowany)"
        SortBy.VOLUME -> "Największa objętość"
        SortBy.MAX_REPS -> "Najwięcej powtórzeń"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label(current),
            onValueChange = {},
            readOnly = true,
            label = { Text("Sortowanie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortBy.entries.forEach {
                DropdownMenuItem(
                    text = { Text(label(it)) },
                    onClick = {
                        onPick(it)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun formatDate(date: String?): String {
    return date?.substring(0, 10) ?: ""
}

private fun computePRs(logs: List<WorkoutLog>): List<PRItem> {
    val map = mutableMapOf<String, MutableList<Triple<Int, Int, String?>>>()

    logs.forEach { w ->
        val date = w.date
        w.exercises.orEmpty().forEach { e ->
            val name = e.name ?: return@forEach
            val weight = e.weight ?: 0
            val reps = e.reps ?: 0
            val sets = maxOf(e.sets ?: 1, 1)

            repeat(sets) {
                map.getOrPut(name) { mutableListOf() }
                    .add(Triple(weight, reps, date))
            }
        }
    }

    return map.map { (name, series) ->
        val maxWeight = series.maxOfOrNull { it.first } ?: 0
        val bestRepsAtMax = series
            .filter { it.first == maxWeight }
            .maxOfOrNull { it.second } ?: 0
        val oneRm = series.maxOfOrNull { (w, r, _) -> epley1RM(w, r) } ?: 0
        val maxVolume = series.maxOfOrNull { (w, r, _) -> w * r } ?: 0
        val lastDate = formatDate(series.maxByOrNull { it.third ?: "" }?.third)

        PRItem(
            name = name,
            maxWeight = maxWeight,
            bestRepsAtMaxWeight = bestRepsAtMax,
            oneRm = oneRm,
            maxVolume = maxVolume,
            lastDate = lastDate
        )
    }.sortedBy { it.name.lowercase() }
}

private fun epley1RM(weight: Int, reps: Int): Int =
    (weight * (1f + reps / 30f)).toInt()
