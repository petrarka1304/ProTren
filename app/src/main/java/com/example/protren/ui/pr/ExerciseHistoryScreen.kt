@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.pr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ExerciseHistoryScreen(navController: NavController, encodedName: String) {
    val name = remember(encodedName) { URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<Triple<String, Int, Int>>>(emptyList()) }

    fun load() {
        scope.launch {
            loading = true; error = null
            try {
                val token = prefs.getAccessToken()
                if (token.isNullOrBlank()) { error = "Sesja wygasła – zaloguj się ponownie."; return@launch }
                val api = ApiClient.createWithAuth(
                    tokenProvider = { token },
                    onUnauthorized = {
                        error = "Sesja wygasła – zaloguj się ponownie."
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                ).create(WorkoutApi::class.java)
                val res = api.getWorkoutLogs(null, null)
                if (res.isSuccessful) {
                    val all: List<WorkoutLog> = res.body().orEmpty()
                    rows = all.flatMap { w ->
                        val d = w.date.orEmpty()
                        w.exercises.orEmpty().filter { it.name == name }.flatMap { e ->
                            val wgt = e.weight ?: 0; val reps = e.reps ?: 0; val sets = e.sets ?: 1
                            List(maxOf(sets, 1)) { Triple(d, wgt, reps) }
                        }
                    }.sortedByDescending { it.first }
                } else error = if (res.code() in listOf(401,403)) "Sesja wygasła – zaloguj się ponownie." else "HTTP ${res.code()}"
            } catch (e: Exception) { error = e.localizedMessage ?: "Nieznany błąd" }
            finally { loading = false }
        }
    }
    LaunchedEffect(name) { load() }

    Scaffold(topBar = { TopAppBar(title = { Text(name) }) }) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Błąd: $error", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { load() }) { Text("Spróbuj ponownie") }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val maxW = rows.maxOfOrNull { it.second } ?: 0
                        val best1RM = rows.maxOfOrNull { (it.second * (1f + it.third / 30f)).toInt() } ?: 0
                        val bestVol = rows.maxOfOrNull { it.second * it.third } ?: 0
                        ElevatedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Najlepsze wyniki", style = MaterialTheme.typography.titleMedium)
                                Text("Max ciężar: $maxW kg", fontWeight = FontWeight.SemiBold)
                                Text("1RM (Epley): $best1RM kg", fontWeight = FontWeight.SemiBold)
                                Text("Max objętość serii: $bestVol kg", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Historia serii", style = MaterialTheme.typography.titleMedium)
                    }
                    items(rows) { (date, w, r) ->
                        ElevatedCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDate(date))
                                Text("$w kg × $r", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
    fun formatDate(date: String?): String {
        return date?.substring(0, 10) ?: "—"
    }

}
