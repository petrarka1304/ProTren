@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.protren.data.UserPreferences
import com.example.protren.navigation.NavItem
import com.example.protren.viewmodel.DashboardUIState
import com.example.protren.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

private object Dimens {
    val ScreenPadding = 16.dp
    val GridSpacing = 12.dp
    val CardCorner = 24.dp
    val SmallCorner = 18.dp
    val BigCardHeight = 140.dp
    val TileHeight = 110.dp
    val BottomExtra = 96.dp
}

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val dashboardState by viewModel.dashboardState.collectAsState()

    // 1) pierwszy load
    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
    }

    // 2) nasłuch na powrót z suplementów
    val backEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backEntry) {
        val prev = navController.previousBackStackEntry
        val changed = prev?.savedStateHandle?.get<Boolean>("supplements_changed") ?: false
        if (changed) {
            viewModel.loadDashboardData()
            prev?.savedStateHandle?.set("supplements_changed", false)
        }
    }

    // nazwa dzisiejszego treningu – NAJPIERW tytuł treningu
    val todayWorkoutName: String? = when (dashboardState) {
        is DashboardUIState.Success -> {
            val s = dashboardState as DashboardUIState.Success
            val w = s.todayWorkout

            // 1) title z logu
            val fromTitle = w?.title?.takeIf { !it.isNullOrBlank() }

            // 2) fallback – pierwsze ćwiczenie
            val fromExercise = w?.exercises?.firstOrNull()?.name

            // 3) fallback – trainingPlanId
            val fromPlan = w?.trainingPlanId

            fromTitle ?: fromExercise ?: fromPlan
        }
        else -> null
    }

    // id dzisiejszego treningu
    val todayWorkoutId: String? =
        (dashboardState as? DashboardUIState.Success)?.todayWorkout?.id

    // liczba suplementów
    val todaySupplementsCount: Int =
        (dashboardState as? DashboardUIState.Success)?.todaySupplementsCount ?: 0

    val todayPlanText = when (dashboardState) {
        DashboardUIState.Loading -> "Ładuję dzisiejszy trening…"
        is DashboardUIState.Error -> "Nie udało się pobrać dzisiejszego treningu"
        is DashboardUIState.Success ->
            if (todayWorkoutName != null)
                "Dziś masz zaplanowany trening: $todayWorkoutName"
            else
                "Dziś nie masz zaplanowanego treningu"
    }

    // 🔙 przechwycenie systemowego „wstecz” na HomeScreenie
    BackHandler(enabled = true) {
        // zamiast cofać – pokaż dialog wylogowania
        showLogoutDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProTren") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Wyloguj się") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Logout, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showLogoutDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { BottomBar(navController) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.BottomExtra)
        ) {

            Text(
                "Witaj ponownie",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Twój przegląd dnia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Dimens.GridSpacing))

            TodayCard(
                text = todayPlanText,
                height = Dimens.BigCardHeight,
                onClick = {
                    if (todayWorkoutId != null) {
                        navController.navigate("workouts/$todayWorkoutId")
                    } else {
                        navController.navigate(NavItem.AddWorkout)
                    }
                }
            )

            Spacer(Modifier.height(Dimens.GridSpacing))

            SectionGrid(
                tiles = listOf(
                    Tile("Dodaj trening", Icons.Filled.SportsGymnastics) {
                        navController.navigate(NavItem.AddWorkout)
                    },
                    Tile("Rekordy osobiste", Icons.Filled.LocalFireDepartment) {
                        navController.navigate("pr")
                    },
                    Tile("Treningi", Icons.Filled.History) {          // 🔁 tu zmiana
                        navController.navigate(NavItem.Workouts)
                    },
                    Tile("Generator planu", Icons.Filled.AutoAwesome) {
                        navController.navigate("autoPlan")
                    }
                )
            )

            Spacer(Modifier.height(Dimens.GridSpacing))

            RemindersCard(
                count = todaySupplementsCount,
                onShow = { navController.navigate(NavItem.SupplementsToday) }
            )

            Spacer(Modifier.height(Dimens.GridSpacing))

            Text(
                "Twoje skróty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(Dimens.GridSpacing))

            SectionGrid(
                tiles = listOf(
                    Tile("Analityka", Icons.Filled.Analytics) {
                        navController.navigate(NavItem.Analytics)
                    },
                    Tile("Plany", Icons.Default.List) {
                        navController.navigate("plans")
                    },
                    Tile("Trenerzy", Icons.Filled.Verified) {
                        navController.navigate("trainerList")
                    },
                    Tile("Suplementy", Icons.Filled.Medication) {
                        navController.navigate(NavItem.SupplementsToday)
                    }
                )
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    // 🔐 Dialog potwierdzenia wylogowania (dla back + menu)
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Wylogować się?") },
            text = { Text("Czy na pewno chcesz się wylogować z ProTren?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            runCatching { prefs.clearTokens() }
                            navController.navigate(NavItem.Login) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                ) {
                    Text("Wyloguj")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

/* ----------------------------- UI helpers ----------------------------- */

@Composable
private fun TodayCard(
    text: String,
    height: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.CardCorner)
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    )
    ElevatedCard(
        onClick = onClick,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Dzisiaj", style = MaterialTheme.typography.titleMedium)
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Zaczynamy",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

private data class Tile(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun SectionGrid(tiles: List<Tile>) {
    FlowRow(
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        tiles.forEach { tile ->
            ElevatedCard(
                onClick = tile.onClick,
                shape = RoundedCornerShape(Dimens.SmallCorner),
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.TileHeight)
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                tile.icon,
                                contentDescription = tile.title,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tile.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RemindersCard(count: Int, onShow: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Suplementy na dziś",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val text = when {
                    count <= 0 -> "Dziś nie masz zaplanowanych suplementów."
                    count == 1 -> "Masz dziś 1 suplement do wzięcia."
                    else -> "Masz dziś $count suplementy do wzięcia."
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onShow) {
                Text("Pokaż")
            }
        }
    }
}
