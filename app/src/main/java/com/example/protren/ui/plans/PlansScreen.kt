@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.plans

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.model.TrainingPlan
import com.example.protren.viewmodel.PlansViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun PlansScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: PlansViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlansViewModel(ctx.applicationContext) as T
        }
    )

    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val plans by vm.plans.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Plany",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!loading && error == null && plans.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                plans.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("autoPlan") }) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Nowy plan")
            }
        }
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> KompaktowyStanBledu(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRetry = { vm.load() }
            )

            plans.isEmpty() -> KompaktowyStanPusty(
                padding = padding,
                onCreate = { navController.navigate("autoPlan") }
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plans, key = { it.id ?: it.name }) { plan ->
                    NowoczesnaKartaPlanu(
                        plan = plan,
                        onOpen = { plan.id?.let { navController.navigate("planDetails/$it") } },
                        onEdit = { plan.id?.let { navController.navigate("planEditor/$it") } },
                        onDelete = {
                            // ProTren: zostawiamy Twoje podejście — edytor ma flow potwierdzenia + DELETE
                            plan.id?.let { navController.navigate("planEditor/$it") }
                        }
                    )
                }
            }
        }
    }
}

/* ───────────────────── STANY ───────────────────── */

@Composable
private fun KompaktowyStanPusty(padding: PaddingValues, onCreate: () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Brak planów",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Stwórz swój pierwszy plan treningowy.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreate) { Text("Nowy plan") }
        }
    }
}

@Composable
private fun KompaktowyStanBledu(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Nie udało się pobrać planów",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

/* ───────────────────── SWIPE TŁO ───────────────────── */

private enum class UkladTlaSwipe { LEWO, PRAWO, SRODEK }

private data class SpecTlaSwipe(
    val tekst: String,
    val ikona: ImageVector,
    val kolorTla: Color,
    val kolorTekstu: Color,
    val uklad: UkladTlaSwipe
)

@Composable
private fun TloDlaSwipe(wartosc: SwipeToDismissBoxValue) {
    val spec = when (wartosc) {
        SwipeToDismissBoxValue.StartToEnd -> SpecTlaSwipe(
            tekst = "Edytuj",
            ikona = Icons.Filled.Edit,
            kolorTla = MaterialTheme.colorScheme.primaryContainer,
            kolorTekstu = MaterialTheme.colorScheme.onPrimaryContainer,
            uklad = UkladTlaSwipe.LEWO
        )

        SwipeToDismissBoxValue.EndToStart -> SpecTlaSwipe(
            tekst = "Usuń",
            ikona = Icons.Filled.Delete,
            kolorTla = MaterialTheme.colorScheme.errorContainer,
            kolorTekstu = MaterialTheme.colorScheme.onErrorContainer,
            uklad = UkladTlaSwipe.PRAWO
        )

        SwipeToDismissBoxValue.Settled -> SpecTlaSwipe(
            tekst = "",
            ikona = Icons.Filled.Edit,
            kolorTla = MaterialTheme.colorScheme.surfaceVariant,
            kolorTekstu = MaterialTheme.colorScheme.onSurfaceVariant,
            uklad = UkladTlaSwipe.SRODEK
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Surface(
            color = spec.kolorTla,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {}

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (spec.uklad) {
                UkladTlaSwipe.LEWO -> Arrangement.Start
                UkladTlaSwipe.PRAWO -> Arrangement.End
                UkladTlaSwipe.SRODEK -> Arrangement.Center
            }
        ) {
            when (spec.uklad) {
                UkladTlaSwipe.LEWO -> {
                    Icon(
                        imageVector = spec.ikona,
                        contentDescription = null,
                        tint = spec.kolorTekstu
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(spec.tekst, color = spec.kolorTekstu, fontWeight = FontWeight.SemiBold)
                }

                UkladTlaSwipe.PRAWO -> {
                    Text(spec.tekst, color = spec.kolorTekstu, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = spec.ikona,
                        contentDescription = null,
                        tint = spec.kolorTekstu
                    )
                }

                UkladTlaSwipe.SRODEK -> {
                    // brak tekstu
                }
            }
        }
    }
}

/* ───────────────────── KARTA + SWIPE ───────────────────── */

@Composable
private fun NowoczesnaKartaPlanu(
    plan: TrainingPlan,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pokazDialogUsun by remember { mutableStateOf(false) }

    val tag = remember(plan.days) { wywnioskujTagPlanu(plan) }

    val dni = plan.days.size
    val cwiczenia = plan.days.sumOf { it.exercises.size }
    val serie = plan.days.sumOf { d -> d.exercises.sumOf { e -> (e.sets ?: 0) } }
    val powtorzeniaSzac = plan.days.sumOf { d ->
        d.exercises.sumOf { e -> (e.sets ?: 0) * max(e.reps ?: 0, 1) }
    }

    val dataTxt = plan.updatedAt?.take(10)
        ?: plan.createdAt?.take(10)
        ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    val scope = rememberCoroutineScope()

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    pokazDialogUsun = true
                    false
                }

                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val docelowaElevacja by animateDpAsState(
        targetValue = if (pressed) 6.dp else 1.dp,
        label = "elevacja_karty_planu"
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            TloDlaSwipe(wartosc = swipeState.targetValue)
        }
    ) {
        ElevatedCard(
            onClick = onOpen,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = docelowaElevacja),
            interactionSource = interactionSource
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            plan.name.ifBlank { "Plan bez nazwy" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Podgląd") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuOpen = false; onOpen() }
                            )
                            DropdownMenuItem(
                                text = { Text("Edytuj") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Usuń") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuOpen = false; pokazDialogUsun = true }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniStatystyka("$dni dni")
                    MiniStatystyka("$cwiczenia ćw.")
                    MiniStatystyka("$serie serii")
                    MiniStatystyka("$powtorzeniaSzac powt.")
                }

                Text(
                    "Aktualizacja: $dataTxt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (pokazDialogUsun) {
        AlertDialog(
            onDismissRequest = { pokazDialogUsun = false },
            title = { Text("Usunąć plan?") },
            text = {
                Text(
                    "Ta operacja jest nieodwracalna.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pokazDialogUsun = false
                        onDelete()
                        scope.launch { swipeState.reset() }
                    }
                ) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pokazDialogUsun = false
                        scope.launch { swipeState.reset() }
                    }
                ) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun MiniStatystyka(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/* ───────────────────── TAGI ───────────────────── */

private fun wywnioskujTagPlanu(plan: TrainingPlan): String {
    val titles = plan.days.joinToString(" ") { it.title }.lowercase()
    return when {
        "full" in titles -> "Pełne ciało"
        listOf("upper", "góra").any { it in titles } &&
                listOf("lower", "dół", "nogi").any { it in titles } -> "Góra / dół"
        listOf("push", "pull", "legs", "nogi").count { it in titles } >= 2 -> "Pchaj / ciągnij / nogi"
        else -> "Własny"
    }
}
