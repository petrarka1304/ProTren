@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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

    var todayLogId by remember { mutableStateOf<String?>(null) }
    var todayLogTitle by remember { mutableStateOf<String?>(null) }
    var todayLogStatus by remember { mutableStateOf<String?>(null) }

    suspend fun refreshTodayFromWorkoutsList() {
        try {
            val token = withContext(Dispatchers.IO) { prefs.getAccessToken() } ?: return
            val api = ApiClient.createWithAuth(
                tokenProvider = { token },
                onUnauthorized = { }
            ).create(WorkoutApi::class.java)

            val res = withContext(Dispatchers.IO) { api.getWorkoutLogs() }
            if (!res.isSuccessful) return

            val today = LocalDate.now().toString()
            val todays = res.body().orEmpty().filter { log ->
                val logDate = log.date?.take(10) ?: ""
                logDate == today
            }

            val done = todays.firstOrNull { it.status == "done" }
            val planned = todays.firstOrNull { it.status == "planned" }
            val pick = done ?: planned ?: todays.firstOrNull()

            todayLogId = pick?.id
            todayLogTitle = pick?.title
            todayLogStatus = pick?.status
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
        refreshTodayFromWorkoutsList()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadDashboardData()
                scope.launch {
                    refreshTodayFromWorkoutsList()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val backEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backEntry) {
        val prev = navController.previousBackStackEntry
        val changed = prev?.savedStateHandle?.get<Boolean>("supplements_changed") ?: false
        if (changed) {
            viewModel.loadDashboardData()
            refreshTodayFromWorkoutsList()
            prev?.savedStateHandle?.set("supplements_changed", false)
        }
    }

    val todayWorkoutNameFromDashboard: String? = when (dashboardState) {
        is DashboardUIState.Success -> {
            val s = dashboardState as DashboardUIState.Success
            val w = s.todayWorkout
            val fromTitle = w?.title?.takeIf { !it.isNullOrBlank() }
            val fromExercise = w?.exercises?.firstOrNull()?.name
            fromTitle ?: fromExercise
        }
        else -> null
    }

    val todaySupplementsCount: Int =
        (dashboardState as? DashboardUIState.Success)?.todaySupplementsCount ?: 0

    val todayPlanText = when (dashboardState) {
        DashboardUIState.Loading -> "Ładuję dzisiejszy trening…"
        is DashboardUIState.Error -> "Błąd pobierania danych"
        is DashboardUIState.Success -> {
            todayLogTitle?.takeIf { it.isNotBlank() }
                ?: todayWorkoutNameFromDashboard
                ?: "Trening"
        }
    }

    BackHandler(enabled = true) {
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
                status = todayLogStatus,
                onClick = {
                    if (todayLogStatus == null) {
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
                    Tile("Treningi", Icons.Filled.History) {
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

@Composable
private fun TodayCard(
    text: String,
    height: Dp,
    status: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.CardCorner)

    val isDone = status == "done"
    val isPlanned = status == "planned"
    val isEnabled = status == null

    val backgroundBrush = when {
        isDone -> Brush.linearGradient(
            listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))
        )
        isPlanned -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        )
        else -> Brush.linearGradient( // Domyślny (Zaczynamy)
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        )
    }

    ElevatedCard(
        onClick = onClick,
        enabled = isEnabled,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (isEnabled) 1f else 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        isDone -> "Świetna robota!"
                        isPlanned -> "Plan na dziś"
                        else -> "Dzisiaj"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (status == null && text == "Trening") "Dodaj trening" else text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }


            Surface(
                modifier = Modifier
                    .size(104.dp)
                    .pointerInput(Unit) { },
                shape = CircleShape,
                color = when {
                    isDone -> Color(0xFF4CAF50)
                    isPlanned -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when {
                                isDone -> Icons.Filled.CheckCircle
                                isPlanned -> Icons.Filled.EventAvailable
                                else -> Icons.Filled.FitnessCenter
                            },
                            contentDescription = null,
                            tint = if (isDone) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                isDone -> "Wykonany"
                                isPlanned -> "Zaplanowano"
                                else -> "Zaczynamy"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isDone) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
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

