package com.example.protren.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ImageViewerScreen(
    nav: NavController,
    startIndex: Int
) {
    val urls = nav.previousBackStackEntry
        ?.savedStateHandle
        ?.get<ArrayList<String>>("image_urls")
        ?.toList()
        ?: emptyList()

    val initialIndex = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    var currentIndex by remember { mutableStateOf(initialIndex) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz"
                        )
                    }
                    Text(
                        text = "${currentIndex + 1} / ${urls.size}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        if (urls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Brak zdjęć do wyświetlenia")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(urls[currentIndex])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentScale = ContentScale.Fit
                )
                if (urls.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickableIf(currentIndex > 0) {
                                    currentIndex--
                                }
                        ) {
                            if (currentIndex > 0) {
                                Text(
                                    "<",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickableIf(currentIndex < urls.lastIndex) {
                                    currentIndex++
                                }
                        ) {
                            if (currentIndex < urls.lastIndex) {
                                Text(
                                    ">",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableIf(
    condition: Boolean,
    onClick: () -> Unit
): Modifier =
    if (condition) {
        this.then(
            Modifier
                .background(Color.Transparent)
                .padding(8.dp)
                .clickable { onClick() }
        )
    } else {
        this
    }
