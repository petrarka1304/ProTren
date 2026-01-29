package com.example.protren.ui.trainer

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.ChatApi
import com.example.protren.network.StartChatRequest
import com.example.protren.network.TrainerCreatePlanRequest
import com.example.protren.network.TrainingPlanDayCreateDto
import com.example.protren.network.TrainingPlanDayDto
import com.example.protren.viewmodel.TrainerPanelViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MEDIA_BASE_URL = "https://protren-backend.onrender.com"
private fun normalizeUrl(raw: String?): String? {
    val v = raw?.trim()
    if (v.isNullOrBlank()) return null
    if (v.startsWith("http://", true) || v.startsWith("https://", true)) return v
    return MEDIA_BASE_URL.trimEnd('/') + "/" + v.trimStart('/')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerTraineesScreen(nav: NavController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val app = LocalContext.current.applicationContext as Application
    val vm: TrainerPanelViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainerPanelViewModel(app) as T
    })

    val prefs = remember { UserPreferences(app) }
    val chatApi by remember {
        mutableStateOf(
            ApiClient.createWithAuth(
                tokenProvider = { prefs.getAccessToken() },
                refreshTokenProvider = { prefs.getRefreshToken() },
                onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
                onUnauthorized = { prefs.clearAll() }
            ).create(ChatApi::class.java)
        )
    }

    val trainees by vm.trainees.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    val filtered = remember(trainees, query) {
        val q = query.trim().lowercase()
        trainees
            .asSequence()
            .filter {
                if (q.isBlank()) true
                else it.name.lowercase().contains(q)
            }
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Podopieczni",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${trainees.size}") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        nav.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("trainer_trainees_count", trainees.size)
                        nav.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchRow(
                query = query,
                onQuery = { query = it },
                onSearch = { focus.clearFocus() },
                onClear = { query = "" }
            )

            AnimatedVisibility(
                visible = loading && trainees.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (!loading && error != null && trainees.isEmpty()) {
                ErrorState(
                    message = error ?: "",
                    onRetry = { vm.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else if (!loading && trainees.isEmpty()) {
                EmptyState(
                    title = "Brak podopiecznych",
                    subtitle = "Gdy zaakceptujesz prośby lub ktoś wykupi współpracę, lista pojawi się tutaj.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
                ) {
                    items(filtered, key = { it.userId }) { t ->
                        var open by remember { mutableStateOf(false) }

                        val safeName = remember(t.name) {
                            t.name.replace("/", "-").replace(" ", "%20")
                        }

                        TraineeCard(
                            name = t.name,
                            email = t.email,
                            avatarUrl = t.avatarUrl,
                            subscriptionActive = t.subscriptionActive == true,
                            subscriptionUntil = t.subscriptionUntil,
                            onCreatePlan = { open = true },
                            onOpenProfile = { nav.navigate("trainer/traineeProfile/${t.userId}") },
                            onPeriodPlan = { nav.navigate("trainerPeriodPlan/${t.userId}") },
                            onChat = {
                                scope.launch {
                                    runCatching {
                                        chatApi.startOrGet(StartChatRequest(userId = t.userId))
                                    }.onSuccess { resp ->
                                        if (resp.isSuccessful && resp.body() != null) {
                                            nav.navigate("chatThread/${resp.body()!!.id}")
                                        } else {
                                            snackbar.showSnackbar(
                                                "Nie udało się otworzyć czatu (HTTP ${resp.code()})"
                                            )
                                        }
                                    }.onFailure {
                                        snackbar.showSnackbar("Błąd sieci – nie udało się otworzyć czatu")
                                    }
                                }
                            },
                            onSupplements = {
                                nav.navigate("trainerSupplements/${t.userId}/$safeName")
                            }
                        )

                        if (open) {
                            CreatePlanDialog(
                                traineeName = t.name,
                                onDismiss = { open = false },
                                onCreate = { planName, dayTitle ->
                                    open = false

                                    vm.createPlanFor(
                                        userId = t.userId,
                                        req = TrainerCreatePlanRequest(
                                            name = planName.trim(),
                                            days = listOf(
                                                TrainingPlanDayCreateDto(
                                                    title = dayTitle.trim().ifBlank { "Dzień" },
                                                    exercises = emptyList()
                                                )
                                            ),
                                            isPublic = false
                                        )
                                    ) { _, msg ->
                                        scope.launch { snackbar.showSnackbar(msg) }
                                    }
                                }
                            )
                        }

                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Szukaj po imieniu i nazwisku…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TraineeCard(
    name: String,
    email: String,
    avatarUrl: String?,
    subscriptionActive: Boolean,
    subscriptionUntil: String?,
    onCreatePlan: () -> Unit,
    onOpenProfile: () -> Unit,
    onPeriodPlan: () -> Unit,
    onChat: () -> Unit,
    onSupplements: () -> Unit = {}
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                val fullUrl = remember(avatarUrl) { normalizeUrl(avatarUrl) }
                if (fullUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fullUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    InitialAvatar(name)
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (email.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            "brak e-maila",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (subscriptionActive) {
                        Spacer(Modifier.height(2.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Aktywna współpraca") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        subscriptionUntil?.let { end ->
                            val dateText = end.take(10)
                            Text(
                                text = "Ważna do: $dateText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onChat) {
                    Icon(Icons.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Czat")
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onSupplements, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Suplementy")
                }

            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onPeriodPlan, modifier = Modifier.weight(1f)) {
                    Text("Plan okresowy")
                }
                OutlinedButton(onClick = onOpenProfile, modifier = Modifier.weight(1f)) {
                    Text("Profil")
                }
            }
        }
    }
}

@Composable
private fun InitialAvatar(name: String) {
    val initial = name.trim().take(1).ifBlank { "?" }.uppercase()
    val tint = remember(initial) {
        val seed = initial.first().code
        val c1 = Color(0xFF7C4DFF + (seed % 50))
        val c2 = Color(0xFF00BCD4 + (abs(seed * 13) % 30))
        listOf(c1, c2)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(tint)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun CreatePlanDialog(
    traineeName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, dayTitle: String) -> Unit
) {
    var name by remember { mutableStateOf("Plan indywidualny") }
    var dayTitle by remember { mutableStateOf("Dzień 1") }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var dayErr by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var ok = true
        if (name.isBlank()) {
            nameErr = "Podaj nazwę planu"; ok = false
        } else nameErr = null
        if (dayTitle.isBlank()) {
            dayErr = "Podaj tytuł dnia"; ok = false
        } else dayErr = null
        return ok
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy plan dla $traineeName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    isError = nameErr != null,
                    supportingText = {
                        if (nameErr != null) Text(
                            nameErr!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    label = { Text("Nazwa planu") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dayTitle,
                    onValueChange = { dayTitle = it },
                    isError = dayErr != null,
                    supportingText = {
                        if (dayErr != null) Text(
                            dayErr!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    label = { Text("Tytuł dnia 1") },
                    singleLine = true
                )
                Text(
                    "Ćwiczenia dodasz później w edytorze planu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (validate()) onCreate(name, dayTitle) }) {
                Text("Utwórz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Coś poszło nie tak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    message.ifBlank { "Spróbuj ponownie za chwilę." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Button(onClick = onRetry) { Text("Odśwież") }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedCard(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}