package com.example.protren.ui.trainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.TraineeProfileResponse
import com.example.protren.network.UserProfileApi
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraineeProfileScreen(
    nav: NavController,
    userId: String
) {
    val context = nav.context
    val prefs = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val snack = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    var loading by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<TraineeProfileResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val api = remember {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val token = prefs.getAccessToken()
                val req = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                chain.proceed(req)
            })
            .build()

        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(UserProfileApi::class.java)
    }

    fun load() {
        loading = true
        error = null
        data = null

        scope.launch {
            runCatching { api.getUserProfile(userId) }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        data = resp.body()
                        if (data == null) error = "Pusta odpowiedź serwera"
                    } else {
                        error = "Błąd ${resp.code()}"
                    }
                }
                .onFailure { e ->
                    error = e.message ?: "Błąd sieci"
                }

            loading = false
            error?.let { snack.showSnackbar(it) }
        }
    }

    LaunchedEffect(userId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil podopiecznego") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
        ) {
            AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                loading && data == null -> {
                    Spacer(Modifier.height(32.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Ładuję profil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                error != null && data == null -> {
                    ErrorCard(message = error ?: "Błąd", onRetry = { load() })
                }

                data != null -> {
                    val d = data!!
                    val realFullName = listOfNotNull(
                        d.firstName?.trim()?.takeIf { it.isNotBlank() },
                        d.lastName?.trim()?.takeIf { it.isNotBlank() }
                    ).joinToString(" ").trim()

                    val nameFromApi = d.name?.trim().orEmpty()

                    val fullName = when {
                        realFullName.isNotBlank() -> realFullName
                        nameFromApi.isNotBlank() && nameFromApi != d.email -> nameFromApi
                        else -> d.email.substringBefore("@").ifBlank { "—" }
                    }

                    TraineeHeader(
                        avatar = d.profile?.avatar,
                        fullName = fullName,
                        email = d.email
                    )

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Informacje", style = MaterialTheme.typography.titleMedium)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Email, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        d.email.ifBlank { "—" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Parametry", style = MaterialTheme.typography.titleMedium)

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MiniMetricCard(
                                        title = "Wiek",
                                        value = d.profile?.age?.toString() ?: "—",
                                        suffix = "lat",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniMetricCard(
                                        title = "Waga",
                                        value = d.profile?.weight?.toString() ?: "—",
                                        suffix = "kg",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniMetricCard(
                                        title = "Wzrost",
                                        value = d.profile?.height?.toString() ?: "—",
                                        suffix = "cm",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(72.dp))
                    }
                }

                else -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Brak danych profilu.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}



@Composable
private fun TraineeHeader(
    avatar: String?,
    fullName: String,
    email: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp)) {
                if (avatar.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Box(Modifier.padding(16.dp)) {
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Nie udało się pobrać profilu", fontWeight = FontWeight.SemiBold)
                }
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Spróbuj ponownie")
                }
            }
        }
    }
}

@Composable
private fun MiniMetricCard(
    title: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    suffix,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
