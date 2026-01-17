package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.model.AddReviewRequest
import com.example.protren.model.Review
import com.example.protren.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val repo: ReviewRepository
) : ViewModel() {

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun loadReviews(trainerId: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = repo.getReviews(trainerId)
            if (res.isSuccessful) {
                _reviews.value = res.body() ?: emptyList()
            }
            _loading.value = false
        }
    }

    fun addReview(
        trainerId: String,
        rating: Int,
        comment: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.addReview(AddReviewRequest(trainerId, rating, comment))
            if (res.isSuccessful) {
                loadReviews(trainerId)
                onSuccess()
            } else onError()
        }
    }

    fun updateReview(
        reviewId: String,
        trainerId: String,
        rating: Int,
        comment: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repo.updateReview(reviewId, AddReviewRequest(trainerId, rating, comment))
            loadReviews(trainerId)
            onSuccess()
        }
    }

    fun deleteReview(reviewId: String, trainerId: String) {
        viewModelScope.launch {
            repo.deleteReview(reviewId)
            loadReviews(trainerId)
        }
    }
}
