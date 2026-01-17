@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.analytics

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart   // ✅ DODANE (niezbędne)
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.network.WeeklySummaryResponse
import com.example.protren.viewmodel.AnalyticsRange
import com.example.protren.viewmodel.AnalyticsState
import com.example.protren.viewmodel.AnalyticsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun AnalyticsScreen(navController: NavController) {
    val app = (LocalContext.current.applicationContext as? Application)
        ?: error("Application context is required")

    val vm: AnalyticsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(app) as T
        }
    })

    val state by vm.state.collectAsState()
    val range by vm.range.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analityka treningów") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }

                    // ✅ DODANE: skrót do śledzonych ćwiczeń (minimalna zmiana)
                    IconButton(onClick = { navController.navigate("trackedExercises") }) {
                        Icon(Icons.Filled.ShowChart, contentDescription = "Śledzone ćwiczenia")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when (val s = state) {
            AnalyticsState.Loading -> {
                LoadingSkeleton(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is AnalyticsState.Error -> {
                ErrorState(
                    message = s.message,
                    onRetry = { vm.load() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is AnalyticsState.Ready -> {
                val data: WeeklySummaryResponse = s.data
                // backend już filtruje po "days", ale na wszelki wypadek:
                val days = data.days.takeLast(range.days)

                val totalVolume = days.sumOf { it.volume }
                val totalSets = days.sumOf { it.sets }
                val totalReps = days.sumOf { it.reps }
                val activeDays = days.count { it.volume > 0 || it.sets > 0 || it.reps > 0 }
                val avgVolume = if (days.isNotEmpty()) totalVolume.toFloat() / days.size else 0f
                val avgSets = if (days.isNotEmpty()) totalSets.toFloat() / days.size else 0f

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Zakres 7 / 14 / 30 dni
                    RangeChipsPretty(selected = range, onSelect = vm::setRange)

                    // Główne podsumowanie
                    SummaryPrettyCard(
                        title = "Podsumowanie (${range.label})",
                        sets = totalSets,
                        reps = totalReps,
                        volume = totalVolume
                    )

                    // Karta aktywności – ładniejsza analityka
                    ActivityCard(
                        daysCount = days.size,
                        activeDays = activeDays,
                        avgVolume = avgVolume,
                        avgSets = avgSets
                    )

                    // Wykres objętości
                    ChartCardPretty(
                        title = "Objętość dzienna",
                        bars = days.map { Bar(it.date.asShortLabel(), it.volume.toFloat()) },
                        valueFormatter = { "${it.toInt()} kg" }
                    )

                    // Wykres serii
                    ChartCardPretty(
                        title = "Serie dziennie",
                        bars = days.map { Bar(it.date.asShortLabel(), it.sets.toFloat()) },
                        valueFormatter = { it.toInt().toString() }
                    )

                    AnimatedVisibility(
                        visible = days.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Brak danych 🤷",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Nie znaleziono treningów w wybranym zakresie.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            else -> {}
        }
    }
}

/* ---------- Range chips ---------- */

@Composable
private fun RangeChipsPretty(selected: AnalyticsRange, onSelect: (AnalyticsRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AnalyticsRange.values().forEachIndexed { idx, r ->
            SegmentedButton(
                selected = selected == r,
                onClick = { onSelect(r) },
                shape = SegmentedButtonDefaults.itemShape(idx, AnalyticsRange.values().size),
                label = { Text(r.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/* ---------- KPI Card ---------- */

@Composable
private fun SummaryPrettyCard(title: String, sets: Int, reps: Int, volume: Int) {
    val shape = RoundedCornerShape(28.dp)
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
        )
    )
    ElevatedCard(shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPill("Serie", sets.toString(), Modifier.weight(1f))
                StatPill("Powtórzenia", reps.toString(), Modifier.weight(1f))
                StatPill("Objętość", "${volume} kg", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActivityCard(
    daysCount: Int,
    activeDays: Int,
    avgVolume: Float,
    avgSets: Float
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Aktywność",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$activeDays / $daysCount dni z treningiem",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPill(
                    "Średnia objętość / dzień",
                    "${avgVolume.toInt()} kg",
                    Modifier.weight(1f)
                )
                StatPill(
                    "Średnia liczba serii / dzień",
                    avgSets.toInt().toString(),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/* ---------- Chart ---------- */

private data class Bar(val label: String, val value: Float)

@Composable
private fun ChartCardPretty(
    title: String,
    bars: List<Bar>,
    valueFormatter: (Float) -> String,
    maxBarHeight: Dp = 180.dp
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            if (bars.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(maxBarHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Brak danych",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 7 dni -> słupki; 14/30 -> linia (czytelniejsze)
                val useLineChart = bars.size > 10
                if (useLineChart) {
                    LineChart(points = bars, height = maxBarHeight)
                } else {
                    BarChart(bars = bars, maxBarHeight = maxBarHeight, valueFormatter = valueFormatter)
                }
            }
        }
    }
}

@Composable
private fun BarChart(
    bars: List<Bar>,
    maxBarHeight: Dp,
    valueFormatter: (Float) -> String
) {
    val maxValue = max(1f, bars.maxOf { it.value })
    val progress by animateFloatAsState(1f, label = "chartIntro")

    val minBarWidth = 16.dp
    val maxBarWidth = 28.dp
    val minGap = 10.dp
    val maxGap = 20.dp

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barGradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = .95f),
            MaterialTheme.colorScheme.primary.copy(alpha = .70f)
        )
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxBarHeight)
            .padding(horizontal = 6.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val px = constraints.maxWidth.toFloat()

        val minBarPx = with(LocalDensity.current) { minBarWidth.toPx() }
        val maxBarPx = with(LocalDensity.current) { maxBarWidth.toPx() }
        val minGapPx = with(LocalDensity.current) { minGap.toPx() }
        val maxGapPx = with(LocalDensity.current) { maxGap.toPx() }

        val candidateBar = (px / (count * 1.8f)).coerceIn(minBarPx, maxBarPx)
        val candidateGap = ((px - candidateBar * count) / (count + 1))
            .coerceIn(minGapPx, maxGapPx)

        Canvas(Modifier.fillMaxSize()) {
            val barW = candidateBar
            val gap = candidateGap
            val radius = CornerRadius(12f, 12f)

            // linie siatki
            repeat(3) { i ->
                val y = size.height * (i + 1) / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
            }

            bars.forEachIndexed { index, bar ->
                val x = (barW + gap) * index + gap
                val h = (size.height * (bar.value / maxValue) * progress)
                    .coerceAtLeast(2f)

                // tło słupka
                drawRoundRect(
                    color = gridColor.copy(alpha = 0.18f),
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = radius
                )
                // właściwy słupek
                drawRoundRect(
                    brush = barGradient,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = radius
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { bar ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 16.dp, max = 44.dp)
            ) {
                Text(
                    valueFormatter(bar.value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    bar.label.take(5),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Czytelniejszy dla 14/30 punktów: pokazuje trend zamiast "lasu słupków".
 * Skala: 0..maxValue, siatka jak w BarChart.
 */
@Composable
private fun LineChart(
    points: List<Bar>,
    height: Dp
) {
    val maxValue = max(1f, points.maxOf { it.value })
    val progress by animateFloatAsState(1f, label = "lineChartIntro")

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val pointFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp)
    ) {
        val count = points.size.coerceAtLeast(1)
        val widthPx = constraints.maxWidth.toFloat()
        val stepX = if (count <= 1) 0f else widthPx / (count - 1)

        Canvas(Modifier.fillMaxSize()) {
            // linie siatki
            repeat(3) { i ->
                val y = size.height * (i + 1) / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
            }

            val path = Path()
            val coords = ArrayList<Offset>(count)

            points.forEachIndexed { index, p ->
                val x = stepX * index
                val y = size.height - (p.value / maxValue * size.height * progress)

                coords += Offset(x, y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // delikatne wypełnienie pod linią (czytelniej, ale bez przesady)
            val fill = Path().apply {
                if (coords.isNotEmpty()) {
                    moveTo(coords.first().x, size.height)
                    lineTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
                    lineTo(coords.last().x, size.height)
                    close()
                }
            }
            drawPath(path = fill, color = pointFill)

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // punkty tylko co kilka (żeby nie było "koralików" na 30 dniach)
            val stride = (count / 8).coerceAtLeast(1)
            coords.forEachIndexed { idx, o ->
                if (idx % stride == 0 || idx == 0 || idx == coords.lastIndex) {
                    drawCircle(
                        color = lineColor,
                        radius = 4.5f,
                        center = o
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(6.dp))

    // Oś X: etykiety rzadziej, żeby dało się to przeczytać dla 14/30
    val n = points.size.coerceAtLeast(1)
    val every = (n / 6).coerceAtLeast(1)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        points.forEachIndexed { index, p ->
            if (index % every == 0 || index == n - 1) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ---------- Stany ---------- */

@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (it == 0) 120.dp else 220.dp)
            ) {}
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ups! Coś poszło nie tak.", style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

/* ---------- Utils ---------- */

private fun String.asShortLabel(): String {
    return runCatching {
        val d = LocalDate.parse(
            this,
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        )
        d.format(DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault()))
    }.getOrElse { this }
}
