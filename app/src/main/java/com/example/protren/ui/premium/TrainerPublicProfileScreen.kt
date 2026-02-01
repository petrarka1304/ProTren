@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.model.Trainer
import com.example.protren.model.Review
import com.example.protren.network.ApiClient
import com.example.protren.viewmodel.TrainerProfileState
import com.example.protren.viewmodel.TrainerProfileViewModel
import com.example.protren.viewmodel.ReviewViewModel

private const val BACKEND_BASE_URL = ApiClient.BASE_URL

@Composable
fun TrainerPublicProfileScreen(
    trainerId: String,
    vm: TrainerProfileViewModel = viewModel(),
    reviewViewModel: ReviewViewModel? = null,
    onAddReviewClick: (trainerId: String) -> Unit = {}
) {
    val state by vm.state.collectAsState()

    val reviewsState =
        reviewViewModel?.reviews?.collectAsState() ?: remember { mutableStateOf(emptyList<Review>()) }
    val reviews = reviewsState.value

    LaunchedEffect(trainerId) {
        if (trainerId == "me") vm.loadMine() else vm.loadPublic(trainerId)
    }

    LaunchedEffect(trainerId, reviewViewModel) {
        if (trainerId != "me" && trainerId.isNotBlank()) {
            reviewViewModel?.loadReviews(trainerId)
        }
    }

    when (val s = state) {
        is TrainerProfileState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is TrainerProfileState.Error -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(s.message)
        }

        is TrainerProfileState.Ready -> {
            val trainer = s.data

            if (trainerId == "me" && reviewViewModel != null) {
                val realId = trainer.id
                LaunchedEffect(realId) {
                    if (!realId.isNullOrBlank()) {
                        reviewViewModel.loadReviews(realId)
                    }
                }
            }

            ProfileContent(
                trainer = trainer,
                ownProfile = trainerId == "me",
                reviews = reviews,
                onAddReviewClick = { id ->
                    if (!id.isNullOrBlank()) {
                        onAddReviewClick(id)
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileContent(
    trainer: Trainer,
    ownProfile: Boolean = false,
    reviews: List<Review> = emptyList(),
    onAddReviewClick: (trainerId: String) -> Unit = {}
) {
    val scroll = rememberScrollState()

    val name = trainer.name.takeIf { it.isNotBlank() } ?: "Trener"
    val headline = trainer.headline?.takeIf { it.isNotBlank() }
    val bio = trainer.bio.orEmpty()
    val specs = trainer.specialties ?: emptyList()
    val price = trainer.priceMonth?.takeIf { it > 0.0 }
    val ratingAvg = trainer.ratingAvg ?: 0.0
    val ratingCount = trainer.ratingCount ?: 0
    val avatar = resolveImageUrl(trainer.avatarUrl)
    val galleryUrls = trainer.galleryUrls ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = (-64).dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatar != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.matchParentSize(),
                                shape = CircleShape
                            ) {}
                            Text(
                                text = name.first().uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        headline?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                StatRow(
                    icon = Icons.Filled.Star,
                    title = String.format("%.1f ★", ratingAvg),
                    meta = if (ratingCount > 0) "($ratingCount opinii)" else "(brak opinii)"
                )

                if (ownProfile && (trainer.traineesCount != null || trainer.workoutsCount != null)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        trainer.traineesCount?.let {
                            MiniStatCard(
                                label = "Podopieczni",
                                value = it.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        trainer.workoutsCount?.let {
                            MiniStatCard(
                                label = "Plany",
                                value = it.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        if (specs.isNotEmpty()) {
            SectionCard(
                icon = Icons.Filled.Tag,
                title = "Specjalizacje"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    specs.forEach { tag ->
                        AssistChip(onClick = {}, enabled = false, label = { Text(tag) })
                    }
                }
            }
        }

        price?.let { p ->
            SectionCard(
                icon = Icons.Filled.LocalOffer,
                title = "Cena od"
            ) {
                Text(
                    text = String.format("%.0f zł / mies.", p),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.Info,
            title = "O trenerze"
        ) {
            Text(
                text = bio.ifBlank { "Brak opisu." },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (galleryUrls.isNotEmpty()) {
            SectionCard(
                icon = Icons.Filled.Photo,
                title = "Portfolio"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    galleryUrls.forEach { raw ->
                        val url = resolveImageUrl(raw)
                        if (url != null) {
                            ElevatedCard(
                                modifier = Modifier
                                    .size(96.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Zdjęcie z portfolio",
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

        SectionCard(
            icon = Icons.Filled.Star,
            title = "Opinie podopiecznych"
        ) {
            if (reviews.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Średnia ocena: ${String.format("%.1f", ratingAvg)} / 5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { (ratingAvg / 5.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                    Text(
                        text = "Na podstawie ${reviews.size} opinii użytkowników.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    reviews.forEach { rev ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${rev.rating} ★",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!rev.comment.isNullOrBlank()) {
                                    Text(
                                        text = rev.comment!!,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text(
                                        text = "Bez komentarza.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Ten trener nie ma jeszcze żadnych opinii. Bądź pierwszą osobą, która podzieli się swoją opinią po współpracy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!ownProfile) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { trainer.id?.let { onAddReviewClick(it) } },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.RateReview, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Napisz opinię")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    meta: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

private fun resolveImageUrl(raw: String?): String? {
    val v = raw?.trim()
    if (v.isNullOrBlank()) return null
    if (v.startsWith("http://", true) || v.startsWith("https://", true)) {
        return v
    }
    return BACKEND_BASE_URL.trimEnd('/') + "/" + v.trimStart('/')
}