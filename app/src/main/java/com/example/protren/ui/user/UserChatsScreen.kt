package com.example.protren.ui.user

import android.app.Application
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.protren.network.ChatSummaryDto
import com.example.protren.network.StartChatRequest
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.GET
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/* ------------------------------------------------------------------
   VIEWMODEL DO LISTY CZATÓW
   ------------------------------------------------------------------ */

class UserChatsViewModel(private val app: Application) : ViewModel() {
    private val prefs = UserPreferences(app)
    private val api by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(ChatApi::class.java)
    }

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<ChatSummaryDto>>(emptyList())
        private set

    suspend fun refresh() {
        loading = true
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
        }
    }

    suspend fun startWithUser(userId: String): String? = try {
        val resp = api.startOrGet(StartChatRequest(userId))
        if (resp.isSuccessful) resp.body()?.id else null
    } catch (_: Exception) {
        null
    }
}

/* ------------------------------------------------------------------
   DTO + API dla pickera trenera (USER -> przypisany trener)
   ------------------------------------------------------------------ */

data class TrainerForUserDto(
    val userId: String,
    val name: String,
    val email: String?,
    val avatarUrl: String?
)

interface TrainersForUserApi {
    // 🚩 nowy endpoint z backendu: GET /api/trainer/my
    @GET("api/trainer/my")
    suspend fun myTrainer(): Response<List<TrainerForUserDto>>
}

/* ------------------------------------------------------------------
   EKRAN LISTY CZATÓW
   ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserChatsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as Application
    val vm: UserChatsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UserChatsViewModel(app) as T
    })

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var newChatOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    val filtered = remember(vm.items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) vm.items
        else vm.items.filter {
            (it.otherName ?: "").lowercase().contains(q) ||
                    (it.lastMessageText ?: "").lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz"
                        )
                    }
                },
                title = { Text("Czaty", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    FilledTonalIconButton(
                        onClick = { scope.launch { vm.refresh() } },
                        enabled = !vm.loading
                    ) {
                        Icon(Icons.Outlined.Chat, contentDescription = "Odśwież")
                    }
                }
            )
        },
        floatingActionButton = {
            FilledTonalIconButton(onClick = { newChatOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nowa rozmowa")
            }
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

            AnimatedVisibility(
                visible = vm.loading && vm.items.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            when {
                !vm.loading && vm.error != null && vm.items.isEmpty() -> ErrorState(
                    message = vm.error ?: "Błąd",
                    onRetry = { scope.launch { vm.refresh() } },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                !vm.loading && filtered.isEmpty() -> EmptyState(
                    title = "Brak rozmów",
                    subtitle = if (query.isBlank())
                        "Rozpocznij konwersację z trenerem."
                    else
                        "Brak wyników dla „$query”.",
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

    if (newChatOpen) {
        NewChatDialog(
            onDismiss = { newChatOpen = false },
            onStart = { userId ->
                newChatOpen = false
                scope.launch {
                    val id = vm.startWithUser(userId.trim())
                    if (id != null) {
                        nav.navigate("chatThread/$id")
                    } else {
                        snackbar.showSnackbar("Nie udało się utworzyć czatu")
                    }
                }
            }
        )
    }
}

/* ------------------------------------------------------------------
   RZĘD CZATU
   ------------------------------------------------------------------ */

@Composable
private fun ChatRowCard(chat: ChatSummaryDto, onClick: () -> Unit) {
    val name = chat.otherName ?: "Rozmówca"
    val preview = chat.lastMessageText ?: "—"
    val unread = chat.unreadCount ?: 0
    val date = chat.lastMessageAt?.let {
        runCatching {
            val inst = Instant.parse(it)
            DateTimeFormatter
                .ofPattern("dd.MM, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(inst)
        }.getOrNull()
    } ?: ""

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(
                name = name,
                avatarUrl = chat.otherAvatarUrl,
                hasUnread = unread > 0
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
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
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            if (unread > 0) {
                Badge {
                    Text(if (unread > 99) "99+" else unread.toString())
                }
            }
        }
    }
}

@Composable
private fun ChatAvatar(name: String, avatarUrl: String?, hasUnread: Boolean) {
    val gradient = remember(name) { initialsGradient(name) }
    val ring =
        if (hasUnread) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

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
                    model = ImageRequest
                        .Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
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
    val c1 = Color(0xFF7C4DFF.toInt() + abs(seed % 30))
    val c2 = Color(0xFF00BCD4.toInt() + abs((seed shr 1) % 30))
    return listOf(c1, c2)
}

/* ------------------------------------------------------------------
   PICKER TRENERA – zamiast wpisywania ID
   ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onStart: (userId: String) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val prefs = remember { UserPreferences(app) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trainers by remember { mutableStateOf<List<TrainerForUserDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    val api = remember {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(TrainersForUserApi::class.java)
    }

    // pobieramy przypisanego trenera z backendu
    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val resp = api.myTrainer()
            if (resp.isSuccessful) {
                trainers = resp.body().orEmpty()
                if (trainers.isEmpty()) {
                    error = "Nie masz jeszcze przypisanego trenera."
                }
            } else {
                error = "Błąd: HTTP ${resp.code()}"
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Błąd sieci"
        } finally {
            loading = false
        }
    }

    val filtered = remember(trainers, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) trainers
        else trainers.filter { t ->
            t.name.lowercase().contains(q) ||
                    (t.email ?: "").lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Wybierz trenera",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Szukaj po imieniu lub emailu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                loading -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null && trainers.isEmpty() -> {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight(0.7f)
                    ) {
                        items(filtered) { t ->
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStart(t.userId)
                                        onDismiss()
                                    },
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t.name
                                                .split(" ")
                                                .take(2)
                                                .joinToString("") {
                                                    it.firstOrNull()?.uppercase() ?: ""
                                                },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            t.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!t.email.isNullOrBlank()) {
                                            Text(
                                                t.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   STANY PUSTY / BŁĄD
   ------------------------------------------------------------------ */

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
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(message)
            }
        }
        FilledTonalIconButton(onClick = onRetry) {
            Icon(Icons.Outlined.Chat, contentDescription = null)
        }
    }
}
