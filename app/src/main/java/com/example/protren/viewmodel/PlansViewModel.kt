package com.example.protren.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainingPlanApi
import com.example.protren.network.toModel
import com.example.protren.model.TrainingPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlansViewModel(private val appContext: Context) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _plans = MutableStateFlow<List<TrainingPlan>>(emptyList())
    val plans: StateFlow<List<TrainingPlan>> = _plans

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val token = UserPreferences(appContext).getAccessToken().orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)
                val res = api.getPlans()
                if (res.isSuccessful) {
                    _plans.value = (res.body() ?: emptyList()).map { it.toModel() }
                } else {
                    _error.value = "Błąd pobierania (${res.code()})"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
            } finally {
                _loading.value = false
            }
        }
    }
}
