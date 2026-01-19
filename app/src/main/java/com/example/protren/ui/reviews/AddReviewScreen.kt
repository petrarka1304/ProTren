package com.example.protren.ui.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.example.protren.viewmodel.ReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReviewScreen(
    trainerId: String,
    viewModel: ReviewViewModel,
    onFinished: () -> Unit
) {
    var rating by remember { mutableStateOf(5f) }
    var comment by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val isSubmitEnabled = !loading && rating >= 1f

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dodaj opinię") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Jak oceniasz tego trenera?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${rating.toInt()} / 5 ★",
                            style = MaterialTheme.typography.titleMedium
                        )

                        RatingStars(rating.toInt())

                        Slider(
                            value = rating,
                            onValueChange = { rating = it.coerceIn(1f, 5f) },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Text(
                            text = when (rating.toInt()) {
                                5 -> "Rewelacyjna współpraca"
                                4 -> "Bardzo dobra współpraca"
                                3 -> "Ok, ale są rzeczy do poprawy"
                                2 -> "Raczej nie polecam"
                                else -> "Zdecydowanie odradzam"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Komentarz (opcjonalnie)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            placeholder = {
                                Text("Napisz kilka słów o swoim doświadczeniu…")
                            },
                            maxLines = 6
                        )
                        Text(
                            text = "${comment.length}/2000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (!loading) onFinished() },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            Text("Anuluj")
                        }
                        Button(
                            onClick = {
                                loading = true
                                viewModel.addReview(
                                    trainerId = trainerId,
                                    rating = rating.toInt(),
                                    comment = comment,
                                    onSuccess = {
                                        loading = false
                                        onFinished()
                                    },
                                    onError = {
                                        loading = false
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isSubmitEnabled
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Zapisz opinię")
                        }
                    }
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text("Zapisujemy Twoją opinię…")
                        }
                    }
                }
            }
        }
    }
}
