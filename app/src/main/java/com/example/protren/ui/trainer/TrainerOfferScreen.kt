@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.protren.ui.trainer

import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.viewmodel.TrainerOfferViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ColumnScope

private const val IMAGE_BASE_URL = "https://protren-backend.onrender.com/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerOfferScreen(
    nav: NavHostController,
    vm: TrainerOfferViewModel
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Moja oferta (trener)") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        when (val s = state) {
            TrainerOfferViewModel.State.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is TrainerOfferViewModel.State.Error -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Nie udało się wczytać oferty",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.load() }) { Text("Spróbuj ponownie") }
                }
            }

            is TrainerOfferViewModel.State.Loaded -> {
                var form by remember(s.offer) { mutableStateOf(s.offer) }

                var avatarUri by remember { mutableStateOf<Uri?>(null) }
                var galleryUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var uploading by remember { mutableStateOf(false) }

                val pickAvatar = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    avatarUri = uri
                    vm.onAvatarSelected(ctx, uri)
                }

                val pickGallery = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
                    val merged = (galleryUris + uris).distinct().take(6)
                    galleryUris = merged
                    vm.onGalleryAdded(ctx, merged)
                }

                var nameErr by remember { mutableStateOf<String?>(null) }
                var emailErr by remember { mutableStateOf<String?>(null) }
                var priceErr by remember { mutableStateOf<String?>(null) }

                val initial = remember(s.offer) { s.offer }

                fun validateAll(): Boolean {
                    var ok = true

                    nameErr = if (form.name.isNullOrBlank()) {
                        ok = false; "Podaj nazwę oferty lub imię i nazwisko"
                    } else null

                    emailErr = when {
                        form.email.isNullOrBlank() -> null
                        !Patterns.EMAIL_ADDRESS.matcher(form.email!!).matches() -> {
                            ok = false; "Nieprawidłowy adres e-mail"
                        }
                        else -> null
                    }

                    priceErr = if (form.priceMonth != null && form.priceMonth!! < 0.0) {
                        ok = false; "Cena nie może być ujemna"
                    } else null

                    return ok
                }

                val isDirty = form != initial || avatarUri != null || galleryUris.isNotEmpty()

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scroll)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    AvatarCard(
                        currentUrl = form.avatarUrl,
                        pickedUri = avatarUri,
                        onPick = { pickAvatar.launch("image/*") },
                        onClear = {
                            avatarUri = null
                            form = form.copy(avatarUrl = null)
                            vm.onAvatarCleared()
                        }
                    )

                    PortfolioCard(
                        existingUrls = form.galleryUrls ?: emptyList(),
                        uris = galleryUris,
                        onAddClick = { pickGallery.launch("image/*") },
                        onRemoveNew = { index ->
                            galleryUris = galleryUris.toMutableList().also {
                                if (index in it.indices) it.removeAt(index)
                            }
                            vm.onGalleryRemoved(index)
                        }
                    )

                    SectionCard(icon = Icons.Outlined.Badge, title = "Dane podstawowe") {
                        OutlinedTextField(
                            value = form.name.orEmpty(),
                            onValueChange = { txt ->
                                form = form.copy(name = txt.trimStart())
                                nameErr = null
                            },
                            label = { Text("Imię i nazwisko / nazwa oferty") },
                            isError = nameErr != null,
                            supportingText = {
                                nameErr?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = form.email.orEmpty(),
                            onValueChange = { txt ->
                                form = form.copy(email = txt.trim())
                                emailErr = null
                            },
                            label = { Text("Email kontaktowy (opcjonalnie)") },
                            leadingIcon = { Icon(Icons.Outlined.Email, null) },
                            isError = emailErr != null,
                            supportingText = {
                                emailErr?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val bio = form.bio.orEmpty()
                        val bioLimit = 600
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { txt -> form = form.copy(bio = txt.take(bioLimit)) },
                            label = { Text("Opis (bio)") },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            minLines = 3,
                            supportingText = { Text("${bio.length}/$bioLimit") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SectionCard(icon = Icons.Outlined.Sell, title = "Specjalizacje") {
                        val all = listOf(
                            "Redukcja", "Masa", "Siła", "Mobilność",
                            "Cross", "Kulturystyka", "Bieganie",
                            "Rehabilitacja", "Dietetyka"
                        )
                        val maxTags = 6
                        val current = form.specialties ?: emptyList()

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            all.forEach { tag ->
                                val selected = current.contains(tag)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val list = current.toMutableList()
                                        if (selected) list.remove(tag)
                                        else if (list.size < maxTags) list.add(tag)
                                        form = form.copy(specialties = list)
                                    },
                                    label = { Text(tag) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Filled.Check, null) }
                                    } else null
                                )
                            }
                        }

                        Text(
                            if ((form.specialties?.size ?: 0) >= maxTags)
                                "Osiągnięto limit $maxTags specjalizacji"
                            else
                                "Możesz wybrać do $maxTags specjalizacji",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SectionCard(icon = Icons.Outlined.Sell, title = "Cennik") {
                        val priceText = form.priceMonth?.let { p ->
                            if (p == 0.0) "" else p.toInt().toString()
                        }.orEmpty()

                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { txt ->
                                val filtered = txt.filter { it.isDigit() }
                                form = form.copy(priceMonth = filtered.toDoubleOrNull())
                                priceErr = null
                            },
                            label = { Text("Cena / miesiąc [PLN]") },
                            leadingIcon = { Icon(Icons.Outlined.Sell, null) },
                            isError = priceErr != null,
                            supportingText = {
                                priceErr?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                } ?: Text(
                                    "Pozostaw puste, jeśli nie chcesz pokazywać ceny.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val showPrice = form.priceMonth != null && form.priceMonth!! > 0.0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                        }
                    }

                    Spacer(Modifier.height(88.dp))

                    val canSave = isDirty && validateAll() && !uploading
                    AnimatedVisibility(
                        visible = canSave,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(tonalElevation = 6.dp) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    when {
                                        uploading -> "Przesyłanie zdjęć…"
                                        else -> "Masz niezapisane zmiany"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    enabled = !uploading,
                                    onClick = {
                                        if (validateAll()) {
                                            vm.updateFrom(form)
                                            scope.launch {
                                                uploading = true
                                                val ok = try {
                                                    vm.uploadImages(ctx, avatarUri, galleryUris)
                                                    vm.save()
                                                } finally {
                                                    uploading = false
                                                }
                                                snackbar.showSnackbar(
                                                    if (ok) "Zapisano " else "Nie udało się zapisać"
                                                )
                                                if (ok) nav.navigateUp()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Zapisz")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun AvatarCard(
    currentUrl: String?,
    pickedUri: Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    SectionCard(icon = Icons.Filled.Photo, title = "Zdjęcie profilowe") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarData: Any? = pickedUri ?: currentUrl
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onPick() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            when (avatarData) {
                                is String -> if (avatarData.startsWith("http", true))
                                    avatarData
                                else
                                    IMAGE_BASE_URL + avatarData.trimStart('/')
                                else -> avatarData
                            }
                        )
                        .crossfade(true)
                        .build(),
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                )
                if (avatarData == null) {
                    Icon(
                        Icons.Filled.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                OutlinedButton(onClick = onPick) { Text("Wybierz zdjęcie") }
                TextButton(
                    onClick = onClear,
                    enabled = (pickedUri != null || currentUrl != null)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Usuń")
                }
            }
        }
    }
}

@Composable
private fun PortfolioCard(
    existingUrls: List<String>,
    uris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveNew: (index: Int) -> Unit
) {
    SectionCard(icon = Icons.Filled.Photo, title = "Portfolio (do 6 zdjęć)") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            existingUrls.forEach { raw ->
                val url = if (raw.startsWith("http", true)) {
                    raw
                } else {
                    IMAGE_BASE_URL + raw.trimStart('/')
                }
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Zdjęcie z portfolio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            uris.forEachIndexed { i, uri ->
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = { onRemoveNew(i) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                    }
                }
            }

            ElevatedCard(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(88.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
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
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}


@Suppress("UNUSED_PARAMETER")
private fun TrainerOfferViewModel.onAvatarSelected(context: Context, uri: Uri?) { }

@Suppress("UNUSED_PARAMETER")
private fun TrainerOfferViewModel.onAvatarCleared() { }

@Suppress("UNUSED_PARAMETER")
private fun TrainerOfferViewModel.onGalleryAdded(context: Context, uris: List<Uri>) { }

@Suppress("UNUSED_PARAMETER")
private fun TrainerOfferViewModel.onGalleryRemoved(index: Int) { }
