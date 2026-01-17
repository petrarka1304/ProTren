package com.example.protren.ui.reviews

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.protren.viewmodel.ReviewViewModel
import kotlinx.coroutines.launch

@Composable
fun ReviewListScreen(
    trainerId: String,
    viewModel: ReviewViewModel,
    onAddReview: () -> Unit
) {
    val reviews by viewModel.reviews.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(true) {
        viewModel.loadReviews(trainerId)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddReview,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (reviews.isEmpty() && !loading) {
                Text("Brak opinii", style = MaterialTheme.typography.bodyLarge)
            }

            reviews.forEach { review ->
                ReviewCard(
                    review = review,
                    onEdit = {
                        // TODO: route to edit screen
                    },
                    onDelete = { viewModel.deleteReview(review.id, trainerId) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ReviewCard(
    review: com.example.protren.model.Review,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "https://cdn-icons-png.flaticon.com/512/149/149071.png",
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(50)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text("Użytkownik", style = MaterialTheme.typography.bodyLarge)
                    RatingStars(review.rating)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(review.comment)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun RatingStars(rating: Int) {
    Row {
        repeat(5) { i ->
            val color =
                if (i < rating) Color(0xFFFFC400)
                else Color.LightGray
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}
