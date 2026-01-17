package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.protren.data.UserPreferences

class HomeViewModelFactory(
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userPreferences) as T
        }
        throw IllegalArgumentException("Nieznana klasa ViewModel")
    }
}
