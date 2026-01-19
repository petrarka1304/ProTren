package com.example.protren.ui.trainer

import android.app.Application
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.ChatApi
import com.example.protren.network.ChatSummaryDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class TrainerChatFilter { ALL, TRAINEES, OTHERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerChatsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as Application
    val vm: TrainerChatsVm = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrainerChatsVm(app) as T
    })

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TrainerChatFilter.ALL) }

    LaunchedEffect(Unit) { vm.refresh(force = true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { vm.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.refresh()
        }
    }

    val items = vm.items
    val loading = vm.loading
    val error = vm.error

    val filtered = remember(items, query, filter) {
        val q = query.trim().lowercase()
        val base = if (q.isBlank()) items else items.filter {
            (it.otherName ?: "").lowercase().contains(q) ||
                    (it.lastMessageText ?: "").lowercase().contains(q)
        }
        when (filter) {
            TrainerChatFilter.ALL -> base
            TrainerChatFilter.TRAINEES -> base.filter { it.relation == "trainee" }
            TrainerChatFilter.OTHERS -> base.filter { it.relation != "trainee" }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Czaty trenera", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    FilledTonalIconButton(
                        onClick = { scope.launch { vm.refresh(force = true) } },
                        enabled = !loading
                    ) {
                        Icon(Icons.Outlined.Chat, contentDescription = "Odśwież")
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Szukaj po nazwie lub treści…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == TrainerChatFilter.ALL,
                    onClick = { filter = TrainerChatFilter.ALL },
                    label = { Text("Wszyscy") }
                )
                FilterChip(
                    selected = filter == TrainerChatFilter.TRAINEES,
                    onClick = { filter = TrainerChatFilter.TRAINEES },
                    label = { Text("Podopieczni") }
                )
                FilterChip(
                    selected = filter == TrainerChatFilter.OTHERS,
                    onClick = { filter = TrainerChatFilter.OTHERS },
                    label = { Text("Inne zapytania") }
                )
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(
                visible = loading && items.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            when {
                !loading && error != null && items.isEmpty() -> ErrorState(
                    message = error ?: "Błąd",
                    onRetry = { scope.launch { vm.refresh(force = true) } },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                !loading && filtered.isEmpty() -> EmptyState(
                    title = "Brak rozmów",
                    subtitle = if (query.isBlank()) "Brak rozmów do wyświetlenia."
                    else "Brak wyników dla „$query”.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 6.dp)
                ) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRowCard(chat = chat) {
                            val otherId = chat.otherUserId ?: ""
                            val encodedName = Uri.encode(chat.otherName ?: "")
                            nav.navigate("chatThread/${chat.id}?otherUserId=$otherId&otherName=$encodedName")
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ChatRowCard(chat: ChatSummaryDto, onClick: () -> Unit) {
    val name = chat.otherName ?: "Rozmówca"
    val preview = chat.lastMessageText ?: "—"
    val unread = chat.unreadCount ?: 0
    val date = chat.lastMessageAt?.let {
        runCatching {
            val inst = Instant.parse(it)
            DateTimeFormatter.ofPattern("dd.MM, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(inst)
        }.getOrNull()
    } ?: ""

    val relationLabel = if (chat.relation == "trainee") "Podopieczny" else "Inne zapytanie"

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(name = name, avatarUrl = chat.otherAvatarUrl, hasUnread = unread > 0)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    if (date.isNotBlank()) {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    preview,
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (chat.relation == "trainee")
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                ) {
                    Text(
                        relationLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chat.relation == "trainee")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (unread > 0) {
                Badge { Text(if (unread > 99) "99+" else unread.toString()) }
            }
        }
    }
}

@Composable
private fun ChatAvatar(name: String, avatarUrl: String?, hasUnread: Boolean) {
    val gradient = remember(name) { initialsGradient(name) }
    val ring = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ring.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(Icons.Outlined.Chat, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private fun initialsGradient(name: String): List<Color> {
    val seed = name.trim().lowercase().hashCode()
    val c1 = Color(0xFF7C4DFF + abs(seed % 30))
    val c2 = Color(0xFF00BCD4 + abs((seed shr 1) % 30))
    return listOf(c1, c2)
}

@Composable
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(message)
            }
        }
        FilledTonalButton(onClick = onRetry) { Text("Odśwież") }
    }
}

private class TrainerChatsVm(app: Application) : ViewModel() {
    private val prefs = UserPreferences(app)
    private val api by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(ChatApi::class.java)
    }

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var items by mutableStateOf<List<ChatSummaryDto>>(emptyList()); private set

    private var refreshRunning = false

    suspend fun refresh(force: Boolean = false) {
        if (refreshRunning) return
        refreshRunning = true

        if (force || items.isEmpty()) loading = true
        error = null

        try {
            val resp = api.list()
            if (resp.isSuccessful) {
                items = resp.body().orEmpty()
            } else {
                error = "HTTP ${resp.code()}"
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Błąd sieci"
        } finally {
            loading = false
            refreshRunning = false
        }
    }
}
