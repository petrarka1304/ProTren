@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.protren.ui.analytics

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private enum class Metric(val label: String) {
    ONE_RM("1RM (szac.)"),
    MAX_WEIGHT("Max ciężar")
}

private data class DayPoint(val date: LocalDate, val value: Float)

@Composable
fun TrackedExercisesProgressScreen(navController: NavController) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    val scope = rememberCoroutineScope()

    var tracked by remember { mutableStateOf(prefs.getTrackedExercises()) }
    var selectedExercise by remember { mutableStateOf(tracked.firstOrNull()) }
    var metric by remember { mutableStateOf(Metric.ONE_RM) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf<List<WorkoutLog>>(emptyList()) }

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

                val api = ApiClient.createWithAuth(
                    tokenProvider = { token },
                    onUnauthorized = { prefs.clearAll() }
                ).create(WorkoutApi::class.java)

                val res = withContext(Dispatchers.IO) { api.getWorkoutLogs() }
                if (res.isSuccessful) {
                    logs = res.body().orEmpty()
                } else {
                    error = "Błąd pobierania danych (HTTP ${res.code()})."
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Nieznany błąd."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    LaunchedEffect(Unit) {
        tracked = prefs.getTrackedExercises()
        if (selectedExercise == null) selectedExercise = tracked.firstOrNull()
        if (selectedExercise != null && selectedExercise !in tracked) {
            selectedExercise = tracked.firstOrNull()
        }
    }

    val points = remember(logs, selectedExercise, metric) {
        buildLast30DaysPoints(logs, selectedExercise, metric)
    }

    val progress = remember(points) { computeProgress(points) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progres (30 dni)") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                }
            )
        }
    ) { pad ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) { Text(error!!, color = MaterialTheme.colorScheme.error) }

            tracked.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) { Text("Nie wybrano żadnych ćwiczeń do śledzenia.") }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExerciseDropdown(
                    items = tracked,
                    selected = selectedExercise ?: tracked.first(),
                    onPick = { selectedExercise = it }
                )

                MetricPicker(metric = metric, onPick = { metric = it })

                ProgressCard(progress, metric)

                ChartCard(points = points, height = 220.dp, metric = metric)
            }
        }
    }
}

@Composable
private fun ExerciseDropdown(items: List<String>, selected: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Ćwiczenie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onPick(name); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun MetricPicker(metric: Metric, onPick: (Metric) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Metric.entries.forEachIndexed { idx, m ->
            SegmentedButton(
                selected = metric == m,
                onClick = { onPick(m) },
                shape = SegmentedButtonDefaults.itemShape(idx, Metric.entries.size),
                label = { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

private data class ProgressUi(
    val start: Float?,
    val end: Float?,
    val delta: Float?,
    val percent: Float?
)

@Composable
private fun ProgressCard(p: ProgressUi, metric: Metric) {
    val unit = "kg"
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progres", fontWeight = FontWeight.SemiBold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.start?.toInt()?.let { "$it $unit" } ?: "—", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Teraz", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.end?.toInt()?.let { "$it $unit" } ?: "—", fontWeight = FontWeight.SemiBold)
            }

            Divider()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Zmiana", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDelta(p.delta, unit), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Procentowo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPercent(p.percent), fontWeight = FontWeight.SemiBold)
            }

            if (metric == Metric.ONE_RM) {
                Text(
                    "1RM to wartość szacowana na podstawie serii (wzór Epleya).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartCard(points: List<DayPoint>, height: Dp, metric: Metric) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trend (30 dni)", fontWeight = FontWeight.SemiBold)

            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                    Text("Brak danych w ostatnich 30 dniach.")
                }
            } else {
                NiceTrendLineChart(points = points, height = height)
            }

            Text(
                "Oś X: dni • Oś Y: ${metric.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NiceTrendLineChart(points: List<DayPoint>, height: Dp) {
    val anim by animateFloatAsState(1f, label = "trendIntro")

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    val fillBrush = remember(lineColor) {
        Brush.verticalGradient(
            listOf(
                lineColor.copy(alpha = 0.18f),
                lineColor.copy(alpha = 0.02f)
            )
        )
    }

    val labelPaint = remember(textColor) {
        Paint().apply {
            color = textColor
            textSize = 28f
            isAntiAlias = true
        }
    }

    val rawMax = points.maxOfOrNull { it.value } ?: 1f
    val rawMin = points.minOfOrNull { it.value } ?: 0f
    val range = (rawMax - rawMin).coerceAtLeast(1f)
    val paddedMax = rawMax + range * 0.15f
    val paddedMin = (rawMin - range * 0.15f).coerceAtLeast(0f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        repeat(3) { i ->
            val y = size.height * (i + 1) / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.2f
            )
        }

        val n = points.size
        val stepX = if (n <= 1) 0f else size.width / (n - 1)

        val coords = ArrayList<Offset>(n)
        points.forEachIndexed { i, p ->
            val x = stepX * i

            val norm = ((p.value - paddedMin) / (paddedMax - paddedMin)).coerceIn(0f, 1f)
            val y = size.height - (norm * size.height * anim)

            coords += Offset(x, y)
        }
        if (coords.isEmpty()) return@Canvas

        fun smoothPath(pts: List<Offset>): Path {
            val path = Path()
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val cur = pts[i]
                val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                path.quadraticBezierTo(prev.x, prev.y, mid.x, mid.y)
            }
            val last = pts.last()
            path.lineTo(last.x, last.y)
            return path
        }

        val linePath = if (coords.size >= 3) smoothPath(coords) else Path().apply {
            moveTo(coords.first().x, coords.first().y)
            coords.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }
        drawPath(path = fillPath, brush = fillBrush)

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        val stride = (coords.size / 8).coerceAtLeast(1)
        coords.forEachIndexed { idx, o ->
            val isFirst = idx == 0
            val isLast = idx == coords.lastIndex
            val shouldDraw = isFirst || isLast || idx % stride == 0
            if (!shouldDraw) return@forEachIndexed

            val r = if (isLast) 6.6f else 4.8f

            drawCircle(
                color = surfaceColor,
                radius = r + 2.2f,
                center = o
            )
            drawCircle(
                color = lineColor,
                radius = r,
                center = o
            )
        }

        val startValue = "${points.first().value.toInt()} kg"
        val endValue = "${points.last().value.toInt()} kg"

        drawContext.canvas.nativeCanvas.drawText(
            startValue,
            coords.first().x + 10f,
            coords.first().y - 12f,
            labelPaint
        )

        val endX = (coords.last().x - 110f).coerceAtLeast(0f)
        drawContext.canvas.nativeCanvas.drawText(
            endValue,
            endX,
            coords.last().y - 12f,
            labelPaint
        )
    }
}


private fun buildLast30DaysPoints(
    logs: List<WorkoutLog>,
    exerciseName: String?,
    metric: Metric
): List<DayPoint> {
    if (exerciseName.isNullOrBlank()) return emptyList()

    val end = LocalDate.now()
    val start = end.minusDays(29)

    val map = mutableMapOf<LocalDate, Float>()

    logs.forEach { w ->
        val raw = w.date ?: return@forEach
        val date = raw.take(10).toLocalDateOrNull() ?: return@forEach
        if (date.isBefore(start) || date.isAfter(end)) return@forEach

        val status = w.status ?: inferStatusFromDate(raw)
        if (status != "done") return@forEach

        w.exercises.orEmpty()
            .filter { it.name?.equals(exerciseName, ignoreCase = true) == true }
            .forEach { e ->
                val weight = (e.weight ?: 0).coerceAtLeast(0)
                val reps = (e.reps ?: 0).coerceAtLeast(0)
                val sets = (e.sets ?: 1).coerceAtLeast(1)

                repeat(sets) {
                    val v = when (metric) {
                        Metric.MAX_WEIGHT -> weight.toFloat()
                        Metric.ONE_RM -> epley1RM(weight, reps).toFloat()
                    }
                    val current = map[date] ?: 0f
                    if (v > current) map[date] = v
                }
            }
    }

    return (0..29).map { i ->
        val d = start.plusDays(i.toLong())
        DayPoint(d, map[d] ?: 0f)
    }.filter { it.value > 0f }
}

private fun computeProgress(points: List<DayPoint>): ProgressUi {
    val nz = points.filter { it.value > 0f }
    if (nz.isEmpty()) return ProgressUi(null, null, null, null)

    val start = nz.first().value
    val end = nz.last().value
    val delta = end - start
    val pct = if (start > 0f) (delta / start) * 100f else null

    return ProgressUi(start = start, end = end, delta = delta, percent = pct)
}

private fun formatDelta(delta: Float?, unit: String): String {
    if (delta == null) return "—"
    val sign = if (delta >= 0f) "+" else ""
    return "$sign${delta.toInt()} $unit"
}

private fun formatPercent(p: Float?): String {
    if (p == null) return "—"
    val sign = if (p >= 0f) "+" else ""
    return "$sign${"%.1f".format(Locale.getDefault(), p)}%"
}

private fun epley1RM(weight: Int, reps: Int): Int {
    return (weight * (1f + reps / 30f)).toInt()
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
}.getOrNull()

private fun inferStatusFromDate(rawDate: String?): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val ld = rawDate?.take(10)?.let { LocalDate.parse(it, formatter) }
        val today = LocalDate.now()
        when {
            ld == null -> "done"
            ld.isAfter(today) -> "planned"
            else -> "done"
        }
    } catch (_: Exception) {
        "done"
    }
}
