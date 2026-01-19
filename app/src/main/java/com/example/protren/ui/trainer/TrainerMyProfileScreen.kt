@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.trainer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.data.UserPreferences
import com.example.protren.model.UserProfile
import com.example.protren.viewmodel.TrainerProfileState
import com.example.protren.viewmodel.TrainerProfileViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.io.InputStream

private val HEADER_HEIGHT = 96.dp
private val AVATAR_SIZE = 64.dp
private val CARD_RADIUS = 20.dp
private val CARD_INNER_PAD = 14.dp
private val TILE_RADIUS = 14.dp

private const val MEDIA_BASE_URL = "https://protren-backend.onrender.com"
private fun normalizeUrl(raw: String?): String? {
    val v = raw?.trim()
    if (v.isNullOrBlank()) return null
    if (v.startsWith("http://", true) || v.startsWith("https://", true)) return v
    return MEDIA_BASE_URL.trimEnd('/') + "/" + v.trimStart('/')
}

data class AvatarUploadResponse(
    val avatarKey: String? = null,
    val avatarUrl: String? = null,
    val profile: UserProfile? = null
)

data class FileViewResponse(
    val url: String
)

interface ProfileUploadApi {
    @Multipart
    @POST("api/profile-uploads/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("meta") metaJson: RequestBody? = null
    ): retrofit2.Response<AvatarUploadResponse>

    @GET("api/files/view")
    suspend fun viewFile(
        @Query("key") key: String
    ): retrofit2.Response<FileViewResponse>

    @GET("api/profile")
    suspend fun getProfile(): retrofit2.Response<UserProfile>
}

@Composable
private fun rememberProfileUploadApi(prefs: UserPreferences): ProfileUploadApi {
    val client = remember {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = prefs.getAccessToken()
                val req = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }.build()
                chain.proceed(req)
            }
            .build()
    }
    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://protren-backend.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
    return remember { retrofit.create(ProfileUploadApi::class.java) }
}

@Composable
fun TrainerMyProfileScreen(
    nav: NavController,
    vm: TrainerProfileViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val prefs = remember { UserPreferences(nav.context) }
    val uploadApi = rememberProfileUploadApi(prefs)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var uploading by remember { mutableStateOf(false) }
    var localAvatarOverrideUrl by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploading = true
                val url = runCatching { uploadAvatar(nav.context, uploadApi, uri) }
                    .getOrNull()
                uploading = false

                if (!url.isNullOrBlank()) {
                    localAvatarOverrideUrl = url
                    vm.loadMine()
                    snackbar.showSnackbar("Avatar zaktualizowany")
                } else {
                    vm.loadMine()
                    snackbar.showSnackbar("Avatar wysłany, ale nie udało się pobrać podglądu")
                }
            }
        }
    }

    LaunchedEffect(Unit) { vm.loadMine() }

    val backStackEntry by nav.currentBackStackEntryAsState()
    var traineesFromTraineesScreen by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(backStackEntry) {
        val fromTrainees = backStackEntry
            ?.savedStateHandle
            ?.get<Int>("trainer_trainees_count")

        if (fromTrainees != null) {
            traineesFromTraineesScreen = fromTrainees
        }
    }

    val scroll = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Panel trenera") },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Ustawienia")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("trainerOffer") },
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                text = { Text("Edytuj ofertę") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        when (val s = state) {
            is TrainerProfileState.Loading -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is TrainerProfileState.Error -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                ErrorCard(
                    message = s.message,
                    onRetry = { vm.loadMine() },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }

            is TrainerProfileState.Ready -> {
                val t = s.data
                val context = LocalContext.current

                val traineesToShow: Int =
                    traineesFromTraineesScreen ?: (t.traineesCount ?: 0)

                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(scroll)
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(CARD_RADIUS)
                    ) {
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(HEADER_HEIGHT)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    )
                                    .padding(horizontal = CARD_INNER_PAD, vertical = 10.dp)
                            ) {
                                Column(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(end = AVATAR_SIZE + 16.dp)
                                ) {
                                    Text(
                                        t.name.ifBlank { "Twój profil" },
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(
                                            onClick = {},
                                            enabled = false,
                                            leadingIcon = { Icon(Icons.Rounded.Verified, null) },
                                            label = { Text("TRAINER") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                        AssistChip(
                                            onClick = {},
                                            enabled = false,
                                            leadingIcon = { Icon(Icons.Rounded.Shield, null) },
                                            label = { Text("Płatności") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                    }
                                }

                                val initials = remember(t.name) {
                                    t.name.trim().takeIf { it.isNotEmpty() }
                                        ?.first()
                                        ?.uppercase()
                                        ?: "T"
                                }

                                val avatarFullUrl = remember(t.avatarUrl, localAvatarOverrideUrl) {
                                    normalizeUrl(localAvatarOverrideUrl ?: t.avatarUrl)
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(AVATAR_SIZE)
                                        .clip(CircleShape)
                                ) {
                                    if (avatarFullUrl != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(avatarFullUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Avatar trenera",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                initials,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Column(Modifier.padding(CARD_INNER_PAD)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { pickImage.launch("image/*") },
                                        enabled = !uploading
                                    ) {
                                        if (uploading) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Wgrywam…")
                                        } else {
                                            Icon(Icons.Rounded.Person, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Zmień avatar")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    StatTile(
                                        title = "Ocena",
                                        value = if (t.ratingAvg == null || t.ratingAvg == 0.0)
                                            "0.0 ★ (brak)"
                                        else
                                            "${"%.1f".format(t.ratingAvg)} ★ (${t.ratingCount ?: 0})",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatTile(
                                        title = "Podopieczni",
                                        value = "$traineesToShow",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    ContentSection(
                        icon = Icons.Outlined.Info,
                        title = "O trenerze",
                        body = t.bio ?: "Nie uzupełniono jeszcze opisu."
                    )

                    ContentSection(
                        icon = Icons.Rounded.Star,
                        title = "Specjalizacje",
                        body = t.specialties
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(" • ")
                            ?: "Dodaj swoje specjalizacje, aby podopieczni szybciej Cię znaleźli."
                    )

                    ContentSection(
                        icon = Icons.Outlined.LocalOffer,
                        title = "Oferta",
                        body = "Abonament: " + (t.priceMonth?.let { "%.0f zł/mies.".format(it) } ?: "—")
                    )

                    val galleryUrls = t.galleryUrls ?: emptyList()
                    if (galleryUrls.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(CARD_RADIUS)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Photo, contentDescription = null)
                                    Text(
                                        "Portfolio (widok trenera)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    galleryUrls.forEach { raw ->
                                        val fullUrl = normalizeUrl(raw)

                                        if (fullUrl != null) {
                                            ElevatedCard(
                                                modifier = Modifier.size(88.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(fullUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Zdjęcie portfolio",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(TILE_RADIUS)
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ContentSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp
) {
    ElevatedCard(
        modifier = modifier
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp + topPadding,
                bottom = 6.dp
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(CARD_RADIUS)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, null)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(CARD_RADIUS)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Nie udało się wczytać profilu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
            }
        }
    }
}

private suspend fun uploadAvatar(
    context: Context,
    api: ProfileUploadApi,
    uri: Uri
): String? {
    val bytes = context.contentResolver
        .openInputStream(uri)
        ?.use(InputStream::readBytes)
        ?: return null

    val body = bytes.toRequestBody(
        "image/*".toMediaTypeOrNull(),
        0,
        bytes.size
    )

    val part = MultipartBody.Part.createFormData(
        name = "avatar",
        filename = "avatar.jpg",
        body = body
    )

    val meta: RequestBody =
        """{"source":"android","folder":"avatars"}"""
            .toRequestBody("application/json".toMediaTypeOrNull())

    val resp = api.uploadAvatar(part, meta)
    if (!resp.isSuccessful) return null

    val r = resp.body()

    val directUrl = r?.avatarUrl
    if (!directUrl.isNullOrBlank()) return directUrl
    val profAvatar = r?.profile?.avatar
    if (!profAvatar.isNullOrBlank() && profAvatar.startsWith("http", ignoreCase = true)) {
        return profAvatar
    }
    val key = r?.avatarKey ?: (profAvatar?.takeIf { !it.startsWith("http", true) })
    if (!key.isNullOrBlank()) {
        val view = api.viewFile(key)
        if (view.isSuccessful) {
            val signed = view.body()?.url
            if (!signed.isNullOrBlank()) return signed
        }
    }

    val refreshed = api.getProfile()
    if (refreshed.isSuccessful) {
        return refreshed.body()?.avatar
    }

    return null
}
