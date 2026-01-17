package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.protren.repository.ReviewRepository

class ReviewViewModelFactory(
    private val repo: ReviewRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReviewViewModel(repo) as T
    }
}
