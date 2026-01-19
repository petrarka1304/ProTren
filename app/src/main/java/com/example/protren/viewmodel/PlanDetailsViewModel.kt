package com.example.protren.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.TrainingPlan
import com.example.protren.model.TrainingPlanDay
import com.example.protren.network.ApiClient
import com.example.protren.network.TrainingPlanApi
import com.example.protren.network.toModel
import com.example.protren.network.WorkoutApi
import com.example.protren.model.CreateWorkoutRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PlanDetailsViewModel(private val appContext: Context) : ViewModel() {
    private val _plan = MutableStateFlow<TrainingPlan?>(null)
    val plan: StateFlow<TrainingPlan?> = _plan

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(id: String) {
        viewModelScope.launch {
            try {
                val token = UserPreferences(appContext).getAccessToken().orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(TrainingPlanApi::class.java)
                val res = api.getPlan(id)
                if (res.isSuccessful) {
                    _plan.value = res.body()?.toModel()
                } else {
                    _error.value = "Błąd pobierania (${res.code()})"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Błąd sieci"
            }
        }
    }
    fun startWorkoutForDay(
        plan: TrainingPlan,
        day: TrainingPlanDay,
        onResult: (ok: Boolean, message: String?, createdId: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = UserPreferences(appContext).getAccessToken().orEmpty()
                val api = ApiClient.createWithAuth(tokenProvider = { token }).create(WorkoutApi::class.java)

                val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                val req = CreateWorkoutRequest(
                    date = today,
                    exercises = day.exercises,
                    trainingPlanId = plan.id
                )

                val res = api.createWorkout(req)
                if (res.isSuccessful) {
                    onResult(true, "Trening zapisany", res.body()?.id)
                } else {
                    onResult(false, "Błąd zapisu (${res.code()})", null)
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Błąd sieci", null)
            }
        }
    }
}
