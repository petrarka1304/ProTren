@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.premium

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.model.Trainer
import com.example.protren.network.ApiClient
import com.example.protren.viewmodel.TrainerListViewModel
import kotlinx.coroutines.launch
private const val MEDIA_BASE_URL = ApiClient.BASE_URL

private fun normalizeUrl(raw: String?): String? {
    val v = raw?.trim()
    if (v.isNullOrBlank()) return null
    if (v.startsWith("http://", true) || v.startsWith("https://", true)) return v
    return MEDIA_BASE_URL.trimEnd('/') + "/" + v.trimStart('/')
}
@Composable
fun TrainerListScreen(navController: NavHostController) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val vm: TrainerListViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrainerListViewModel(app) as T
            }
        }
    )

    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val trainers by vm.items.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Znajdź trenera", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(padding)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nie udało się pobrać listy trenerów",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))

                        val errText = error?.toString().orEmpty().trim()
                        if (errText.isNotBlank()) {
                            Text(
                                errText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        OutlinedButton(onClick = { vm.load() }) {
                            Text("Spróbuj ponownie")
                        }
                    }
                }

                trainers.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Na razie nie ma dostępnych trenerów.\nSpróbuj ponownie później.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                ) {
                    items(trainers) { t ->
                        val offerId = t.stableId.trim()
                        val trainerUserId = (t.userId ?: "").trim()

                        val current = t.traineesCount ?: 0
                        val max = t.maxTrainees ?: 10
                        val isFull = current >= max

                        TrainerCard(
                            t = t,
                            current = current,
                            max = max,
                            isFull = isFull,

                            onDetails = {
                                if (offerId.isNotBlank()) {
                                    navController.navigate("trainerProfile/$offerId")
                                } else {
                                    scope.launch {
                                        snackbar.showSnackbar("Brak identyfikatora oferty trenera.")
                                    }
                                }
                            },

                            onPurchase = {
                                if (offerId.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("Brak identyfikatora oferty trenera.") }
                                    return@TrainerCard
                                }

                                if (isFull) {
                                    scope.launch {
                                        snackbar.showSnackbar("Ten trener nie ma już wolnych miejsc.")
                                    }
                                } else {
                                    navController.navigate("purchaseTrainer/$offerId")
                                }
                            },

                            onMessage = {
                                if (trainerUserId.isNotBlank()) {
                                    navController.navigate("chatStart/$trainerUserId")
                                } else {
                                    scope.launch {
                                        snackbar.showSnackbar("Nie można rozpocząć czatu: brak ID użytkownika trenera.")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainerCard(
    t: Trainer,
    current: Int,
    max: Int,
    isFull: Boolean,
    onDetails: () -> Unit,
    onPurchase: () -> Unit,
    onMessage: () -> Unit,
) {
    val name = t.name.takeIf { it.isNotBlank() } ?: "Trener"
    val specialties = t.specialties ?: emptyList()
    val tags = specialties.joinToString(" • ")
    val bio = t.bio.orEmpty()
    val ratingAvg = t.ratingAvg ?: 0.0
    val ratingCount = t.ratingCount ?: 0
    val priceText = t.priceMonth?.let { "${it.toInt()} zł / mies." } ?: "Cena ustalana indywidualnie"
    val avatarUrl = remember(t.avatarUrl) { normalizeUrl(t.avatarUrl) }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar trenera",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tags.isNotBlank()) {
                        Text(
                            tags,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        val label = if (ratingCount > 0) "${"%.1f".format(ratingAvg)} ★" else "Brak ocen"
                        Text(label)
                    }
                )
            }

            if (bio.isNotBlank()) {
                Text(
                    bio,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Współpraca miesięczna",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            priceText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = "Podopieczni: $current / $max",
                style = MaterialTheme.typography.bodySmall,
                color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isFull) {
                Text(
                    text = "Brak wolnych miejsc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val btnMod = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)

                OutlinedButton(
                    onClick = onDetails,
                    modifier = btnMod
                ) {
                    Text(
                        "Szczegóły",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onMessage,
                    modifier = btnMod
                ) {
                    Text(
                        "Napisz",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onPurchase,
                    modifier = btnMod,
                    enabled = !isFull
                ) {
                    Text(
                        if (isFull) "Brak miejsc" else "Wykup trenera",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}