package com.example.protren.ui.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    nav: NavController,
    startIndex: Int
) {
    // Odbierz listę URL-i ze SavedStateHandle (ustawianą przed nawigacją)
    val urls = remember {
        nav.previousBackStackEntry?.savedStateHandle?.get<List<String>>("image_urls")
            ?: nav.currentBackStackEntry?.savedStateHandle?.get<List<String>>("image_urls")
            ?: emptyList()
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)),
        pageCount = { urls.size.coerceAtLeast(1) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val total = urls.size.coerceAtLeast(1)
                    val current = (pagerState.currentPage + 1).coerceAtMost(total)
                    Text("$current / $total")
                },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Zamknij")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (urls.isEmpty()) {
                Text("Brak obrazu", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val url = urls[page]

                    // Stan transformacji dla TEJ strony
                    var scale by remember(page) { mutableFloatStateOf(1f) }
                    var offsetX by remember(page) { mutableFloatStateOf(0f) }
                    var offsetY by remember(page) { mutableFloatStateOf(0f) }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                        if (newScale <= 1.01f) {
                            // przy małym zoomie blokujemy przesuwanie
                            offsetX = 0f; offsetY = 0f
                        } else {
                            offsetX += panChange.x
                            offsetY += panChange.y
                        }
                        scale = newScale
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = offsetX
                                translationY = offsetY
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(page) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale < 1.5f) {
                                            scale = 2.5f
                                        } else {
                                            scale = 1f
                                            offsetX = 0f; offsetY = 0f
                                        }
                                    }
                                )
                            }
                            .transformable(transformState)
                    )
                }
            }
        }
    }
}
