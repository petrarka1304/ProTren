package com.example.protren.ui.trainer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import com.example.protren.network.ChatAttachmentDto
import com.example.protren.network.ChatMessageDto
import com.example.protren.network.ReplyRef
import com.example.protren.viewmodel.ChatThreadViewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHAT_MEDIA_BASE_URL = ApiClient.BASE_URL


private fun normalizeMediaUrl(raw: String?): String? {
    val v = raw?.trim()
    if (v.isNullOrBlank()) return null
    if (v.startsWith("http://", true) || v.startsWith("https://", true)) return v
    return CHAT_MEDIA_BASE_URL.trimEnd('/') + "/" + v.trimStart('/')
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    nav: NavController,
    chatId: String,
    otherUserId: String? = null,
    otherNameInitial: String? = null
) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current

    val userPrefs = remember { UserPreferences(app) }
    val authToken = remember { userPrefs.getAccessToken() }

    val vm: ChatThreadViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatThreadViewModel(app) as T
    })

    val messages by vm.messages.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val typing by vm.otherTyping.collectAsState()
    val otherName by vm.otherName.collectAsState()
    val otherAvatar by vm.otherAvatarUrl.collectAsState()
    val vmOtherUserId by vm.otherUserId.collectAsState()
    val effectiveOtherUserId = vmOtherUserId ?: otherUserId
    val myUserId by vm.myUserId.collectAsState()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var replyTo by remember { mutableStateOf<ChatMessageDto?>(null) }
    var longPressed by remember { mutableStateOf<ChatMessageDto?>(null) }

    var pendingImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingImageCaption by remember { mutableStateOf(TextFieldValue("")) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pendingImageUris = uris
            pendingImageCaption = TextFieldValue("")
        }
    }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.sendVideo(uri, replyToId = replyTo?.id) { ok ->
            if (ok) {
                replyTo = null
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }

    var showAttachDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) { vm.load(chatId) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(Unit) { vm.markRead() }

    LaunchedEffect(otherNameInitial) {
        if (otherNameInitial != null) {
            vm.setOther(otherNameInitial, null)
        }
    }

    LaunchedEffect(otherUserId) {
        if (otherUserId != null) {
            vm.setOtherUserId(otherUserId)
        }
    }

    fun openVideo(url: String) {
        val full = normalizeMediaUrl(url) ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(full)).apply {
            setDataAndType(Uri.parse(full), "video/*")
        }
        context.startActivity(intent)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatarUrl = normalizeMediaUrl(otherAvatar)

                        val displayName = otherName ?: otherNameInitial ?: "?"
                        val initials = displayName.trim().firstOrNull()?.uppercase() ?: "?"

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Column {
                if (pendingImageUris.isNotEmpty()) {
                    PendingImagesBar(
                        uris = pendingImageUris,
                        caption = pendingImageCaption,
                        onCaptionChange = { pendingImageCaption = it },
                        onCancel = {
                            pendingImageUris = emptyList()
                            pendingImageCaption = TextFieldValue("")
                        },
                        onSend = {
                            val urisToSend = pendingImageUris
                            if (urisToSend.isNotEmpty()) {
                                vm.sendImages(
                                    uris = urisToSend,
                                    replyToId = replyTo?.id,
                                    caption = pendingImageCaption.text.takeIf { it.isNotBlank() }
                                ) { ok ->
                                    if (ok) {
                                        pendingImageUris = emptyList()
                                        pendingImageCaption = TextFieldValue("")
                                        replyTo = null
                                        keyboardController?.hide()
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                }
                            }
                        }
                    )
                }

                InputBar(
                    text = input,
                    onTextChange = {
                        input = it
                        vm.setTyping(it.text.isNotBlank())
                    },
                    onAttach = { showAttachDialog = true },
                    onSend = {
                        val txt = input.text.trim()
                        if (txt.isNotEmpty()) {
                            vm.send(txt, replyToId = replyTo?.id) { ok ->
                                if (ok) {
                                    input = TextFieldValue("")
                                    replyTo = null
                                    keyboardController?.hide()
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            }
                        }
                    },
                    replyPreview = replyTo?.let { { ReplyPreview(it) { replyTo = null } } }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(padding)
        ) {
            when {
                loading && messages.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null && messages.isEmpty() ->
                    Text("Błąd: $error", modifier = Modifier.align(Alignment.Center))

                else -> {
                    MessagesList(
                        messages = messages,
                        listState = listState,
                        otherUserId = effectiveOtherUserId,
                        otherAvatarUrl = otherAvatar,
                        authToken = authToken,
                        onImageClick = { urls, idx ->
                            nav.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set("image_urls", ArrayList(urls))
                            nav.navigate("imageViewer/$idx")
                        },
                        onVideoClick = { url -> openVideo(url) },
                        onLongPress = { longPressed = it },
                        onReply = { replyTo = it },
                        onLoadMore = { vm.loadMore() }
                    )
                }
            }

            TypingIndicator(
                visible = typing,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 72.dp)
            )
        }
    }

    longPressed?.let { msg ->
        val mine = effectiveOtherUserId != null && msg.senderId != effectiveOtherUserId
        MessageActionSheet(
            mine = mine,
            onDismiss = { longPressed = null },
            onReply = { replyTo = msg },
            onCopy = { msg.text?.let { clipboard.setText(AnnotatedString(it)) } },
            onDelete = {
                vm.delete(msg.id) { ok ->
                    if (ok) longPressed = null
                }
            }
        )
    }

    if (showAttachDialog) {
        AttachDialog(
            onDismiss = { showAttachDialog = false },
            onPickImages = {
                showAttachDialog = false
                pickImages.launch("image/*")
            },
            onPickVideo = {
                showAttachDialog = false
                pickVideo.launch("video/*")
            }
        )
    }
}


@Composable
private fun MessagesList(
    messages: List<ChatMessageDto>,
    listState: LazyListState,
    otherUserId: String?,
    otherAvatarUrl: String?,
    authToken: String?,
    onImageClick: (urls: List<String>, startIdx: Int) -> Unit,
    onVideoClick: (url: String) -> Unit,
    onLongPress: (ChatMessageDto) -> Unit,
    onReply: (ChatMessageDto) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
            if (index == messages.lastIndex) {
                LaunchedEffect(Unit) { onLoadMore() }
            }

            val created = runCatching {
                OffsetDateTime.parse(msg.createdAt)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
            }.getOrNull()

            val timeText = created?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""

            MessageRow(
                msg = msg,
                timeText = timeText,
                otherUserId = otherUserId,
                otherAvatarUrl = otherAvatarUrl,
                authToken = authToken,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onLongPress = onLongPress,
                onReply = onReply
            )

            if (index == 0) {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessageDto,
    timeText: String,
    otherUserId: String?,
    otherAvatarUrl: String?,
    authToken: String?,
    onImageClick: (urls: List<String>, startIdx: Int) -> Unit,
    onVideoClick: (url: String) -> Unit,
    onLongPress: (ChatMessageDto) -> Unit,
    onReply: (ChatMessageDto) -> Unit
) {
    val mine = otherUserId != null && msg.senderId != otherUserId

    val avatarFullUrl = remember(otherAvatarUrl) { normalizeMediaUrl(otherAvatarUrl) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            if (avatarFullUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarFullUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp, bottom = 20.dp)
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp, bottom = 20.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }


        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {

            if (!mine && !msg.senderName.isNullOrBlank()) {
                Text(
                    msg.senderName!!,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
            }

            Bubble(
                msg = msg,
                mine = mine,
                authToken = authToken,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onLongPress = { onLongPress(msg) },
                onReply = { onReply(msg) }
            )

            Spacer(Modifier.height(2.dp))

            Text(
                timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun Bubble(
    msg: ChatMessageDto,
    mine: Boolean,
    authToken: String?,
    onImageClick: (urls: List<String>, startIdx: Int) -> Unit,
    onVideoClick: (url: String) -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit
) {
    val shape =
        if (mine)
            RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
        else
            RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)

    val gradient =
        if (mine)
            Brush.linearGradient(
                listOf(
                    Color(0xFF0A84FF),
                    Color(0xFF5AC8FA)
                )
            )
        else
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                )
            )

    val textColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 80.dp)
            .wrapContentWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onReply() }
                )
            },
        shape = shape,
        tonalElevation = 0.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(gradient, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                if (!msg.text.isNullOrBlank()) {
                    Text(
                        msg.text!!,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val attachments = msg.attachments.orEmpty()
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    ImageMosaic(
                        attachments = attachments,
                        authToken = authToken,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick
                    )
                }

                msg.replyTo?.let { ref ->
                    Spacer(Modifier.height(4.dp))
                    ReplyStub(ref, mine)
                }
            }
        }
    }
}


@Composable
private fun ImageMosaic(
    attachments: List<ChatAttachmentDto>,
    authToken: String?,
    onImageClick: (urls: List<String>, startIdx: Int) -> Unit,
    onVideoClick: (url: String) -> Unit
) {
    val images = attachments.filter { it.type.equals("image", ignoreCase = true) }
    val videos = attachments.filter { it.type.equals("video", ignoreCase = true) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (images.isNotEmpty()) {
            val urls = images.mapNotNull { normalizeMediaUrl(it.url) }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                images.take(3).forEachIndexed { index, att ->
                    val imageUrl = normalizeMediaUrl(att.url)

                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImageClick(urls, index) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        videos.forEach { video ->
            val videoUrl = normalizeMediaUrl(video.url) ?: return@forEach

            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVideoClick(videoUrl) }
            ) {
                Row(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wideo", style = MaterialTheme.typography.bodyMedium)
                        video.durationMs?.let {
                            Text(
                                "${it / 1000}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text("pisze…", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ReplyStub(original: ReplyRef, mine: Boolean) {
    val bg = if (mine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp,
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                original.senderName ?: "",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (mine) Color.White else MaterialTheme.colorScheme.primary
            )
            Text(
                original.text ?: "(załącznik)",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InputBar(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    replyPreview: (@Composable () -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            replyPreview?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) { it() }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Załącz")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Napisz wiadomość…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        keyboardController?.hide()
                        onSend()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(original: ChatMessageDto, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    original.senderName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    original.text ?: "(obraz)",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onCancel) { Text("Anuluj") }
        }
    }
}

@Composable
private fun MessageActionSheet(
    mine: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Akcje", style = MaterialTheme.typography.titleMedium)


                AssistChip(
                    onClick = {
                        onCopy()
                        onDismiss()
                    },
                    label = { Text("Kopiuj tekst") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
                )

                if (mine) {
                    AssistChip(
                        onClick = {
                            onDelete()
                        },
                        label = { Text("Usuń wiadomość") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
                    )
                }

                AssistChip(
                    onClick = onDismiss,
                    label = { Text("Zamknij") }
                )
            }
        }
    )
}


@Composable
private fun AttachDialog(
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wyślij załącznik", style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = onPickImages,
                    label = { Text("Zdjęcia") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                )
                AssistChip(
                    onClick = onPickVideo,
                    label = { Text("Wideo") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) }
                )
                AssistChip(
                    onClick = onDismiss,
                    label = { Text("Anuluj") }
                )
            }
        }
    )
}

@Composable
private fun PendingImagesBar(
    uris: List<Uri>,
    caption: TextFieldValue,
    onCaptionChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                "Wybrane zdjęcia",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uris.take(3).forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (uris.size > 3) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+${uris.size - 3}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Dodaj podpis (opcjonalnie)…") },
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) { Text("Anuluj") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onSend) { Text("Wyślij zdjęcia") }
            }
        }
    }
}
