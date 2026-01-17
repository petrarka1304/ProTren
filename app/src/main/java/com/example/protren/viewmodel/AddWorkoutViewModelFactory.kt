package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.protren.data.UserPreferences

class AddWorkoutViewModelFactory(
    private val prefs: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddWorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddWorkoutViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
