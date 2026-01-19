package com.example.protren.ui.trainer.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.protren.network.TrainerPlanApi


class TrainerPlansViewModelFactory(
    private val api: TrainerPlanApi
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TrainerPlansViewModel(api) as T
    }
}
