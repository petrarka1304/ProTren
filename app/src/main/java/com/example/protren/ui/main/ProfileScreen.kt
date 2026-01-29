@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.main

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.protren.model.UserProfile
import com.example.protren.viewmodel.ProfileUIState
import com.example.protren.viewmodel.ProfileViewModel
import com.example.protren.viewmodel.ProfileViewModelFactory
import com.example.protren.viewmodel.ProfileAvatarViewModel
import com.example.protren.viewmodel.AccountViewModel
import com.example.protren.viewmodel.AccountViewModelFactory
import com.example.protren.viewmodel.AccountUIState

@Composable
fun ProfileScreen(
    navController: NavController
) {
    val app = (LocalContext.current.applicationContext as? Application)
        ?: error("Application context is required")

    val vm: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(app))
    val avatarVm: ProfileAvatarViewModel = viewModel()

    val accountVm: AccountViewModel = viewModel(factory = AccountViewModelFactory(app))
    val accountState by accountVm.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accountVm.loadMe()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val state by vm.state.collectAsState()
    val uploadedAvatar by avatarVm.avatarUrl.collectAsState()
    val uploadLoading by avatarVm.loading.collectAsState()
    val uploadError by avatarVm.error.collectAsState()

    val snack = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    var form by remember { mutableStateOf(UserProfile()) }

    val displayName by remember(accountState) {
        mutableStateOf(
            when (val s = accountState) {
                is AccountUIState.Loaded -> {
                    val me = s.me
                    val fromFull = me.fullName?.takeIf { it.isNotBlank() }
                    val fromParts = listOfNotNull(me.firstName, me.lastName)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                    fromFull ?: fromParts ?: me.email ?: ""
                }
                is AccountUIState.Saved -> {
                    val me = s.me
                    if (me == null) "" else {
                        val fromFull = me.fullName?.takeIf { it.isNotBlank() }
                        val fromParts = listOfNotNull(me.firstName, me.lastName)
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                        fromFull ?: fromParts ?: me.email ?: ""
                    }
                }
                else -> ""
            }
        )
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is ProfileUIState.Loaded -> form = s.profile
            is ProfileUIState.Saved -> s.info?.let { snack.showSnackbar(it) }
            is ProfileUIState.Error -> snack.showSnackbar(s.message)
            ProfileUIState.Loading -> Unit
        }
    }
    LaunchedEffect(uploadError) { uploadError?.let { snack.showSnackbar(it) } }

    LaunchedEffect(uploadedAvatar) {
        uploadedAvatar?.let { url ->
            form = form.copy(avatar = url)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { chosen ->
            avatarVm.upload(chosen) { ok, url ->
                if (ok && url != null) {
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Mój profil") },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("settings") },
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "Ustawienia konta")
                    }


                    IconButton(
                        onClick = { vm.saveProfile(form) },
                        enabled = state !is ProfileUIState.Loading
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Zapisz")
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
            ProfileHeader(
                avatar = form.avatar,
                title = if (displayName.isNotBlank()) displayName else "Profil użytkownika",
                subtitle = "Ustaw parametry i przelicz makra",
                uploading = uploadLoading,
                onChangeAvatar = { pickImage.launch("image/*") },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Parametry
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Parametry", style = MaterialTheme.typography.titleMedium)

                        NumbersGrid(
                            age = form.age?.toString().orEmpty(),
                            height = form.height?.toString().orEmpty(),
                            weight = form.weight?.toString().orEmpty(),
                            onAge = { form = form.copy(age = it.toIntOrNull()) },
                            onHeight = { form = form.copy(height = it.toIntOrNull()) },
                            onWeight = { form = form.copy(weight = it.toIntOrNull()) }
                        )

                        ExposedDropdown(
                            label = "Płeć",
                            current = form.gender ?: "male",
                            options = listOf("male" to "Mężczyzna", "female" to "Kobieta")
                        ) { form = form.copy(gender = it) }

                        ExposedDropdown(
                            label = "Cel",
                            current = form.goal ?: "utrzymanie",
                            options = listOf(
                                "redukcja" to "Redukcja",
                                "masa" to "Masa",
                                "utrzymanie" to "Utrzymanie"
                            )
                        ) { form = form.copy(goal = it) }

                        ExposedDropdown(
                            label = "Aktywność",
                            current = form.activityLevel ?: "moderately_active",
                            options = listOf(
                                "sedentary" to "Siedzący",
                                "lightly_active" to "Lekka (1–2/tyg)",
                                "moderately_active" to "Umiarkowana (3–5/tyg)",
                                "very_active" to "Wysoka (6–7/tyg)",
                                "extra_active" to "Bardzo wysoka / 2x dziennie"
                            )
                        ) { form = form.copy(activityLevel = it) }

                        AnimatedVisibility(
                            visible = state is ProfileUIState.Loading,
                            enter = fadeIn(), exit = fadeOut()
                        ) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        Text(
                            "Zapotrzebowanie i makra",
                            style = MaterialTheme.typography.titleMedium
                        )
                        MacrosRow(
                            calories = form.calories,
                            protein = form.protein,
                            fat = form.fat,
                            carbs = form.carbs
                        )
                        Text(
                            "Użyj ikony w pasku u góry, aby przeliczyć wartości.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (form.subscriptionActive == true) {
                    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Współpraca z trenerem",
                                style = MaterialTheme.typography.titleMedium
                            )

                            val trainerId = form.trainerId
                            if (!trainerId.isNullOrBlank()) {
                                Text(
                                    text = "ID trenera: $trainerId",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            form.subscriptionUntil?.let { end ->
                                val dateText = end.take(10) // yyyy-MM-dd
                                Text(
                                    text = "Ważna do: $dateText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (trainerId.isNullOrBlank() && form.subscriptionUntil == null) {
                                Text(
                                    text = "Masz aktywną współpracę z trenerem.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    avatar: String?,
    title: String,
    subtitle: String,
    uploading: Boolean,
    onChangeAvatar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (avatar.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.18f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Person, contentDescription = null) }
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
                FilledTonalIconButton(
                    onClick = onChangeAvatar,
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = 2.dp, y = 2.dp),
                    enabled = !uploading
                ) {
                    if (uploading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Outlined.Edit, contentDescription = "Zmień zdjęcie")
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
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
private fun NumbersGrid(
    age: String, height: String, weight: String,
    onAge: (String) -> Unit, onHeight: (String) -> Unit, onWeight: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = age,
            onValueChange = { onAge(it.filter(Char::isDigit)) },
            label = { Text("Wiek (lata)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = height,
                onValueChange = { onHeight(it.filter(Char::isDigit)) },
                label = { Text("Wzrost (cm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { onWeight(it.filter(Char::isDigit)) },
                label = { Text("Waga (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MacrosRow(calories: Int?, protein: Int?, fat: Int?, carbs: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MacroCard("Kalorie", calories?.toString() ?: "—", Modifier.weight(1f))
        MacroCard("Białko [g]", protein?.toString() ?: "—", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MacroCard("Tłuszcz [g]", fat?.toString() ?: "—", Modifier.weight(1f))
        MacroCard("Węgle [g]", carbs?.toString() ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun MacroCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExposedDropdown(
    label: String,
    current: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = options.find { it.first == current }?.second ?: current,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (code, labelPl) ->
                DropdownMenuItem(
                    text = { Text(labelPl) },
                    onClick = {
                        onPick(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
