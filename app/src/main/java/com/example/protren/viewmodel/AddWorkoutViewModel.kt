package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.WorkoutApi
import com.example.protren.model.CreateWorkoutRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import retrofit2.Response

sealed class AddWorkoutState {
    object Idle : AddWorkoutState()
    object Loading : AddWorkoutState()
    data class Success(val workout: WorkoutLog?) : AddWorkoutState()
    data class Error(val message: String) : AddWorkoutState()
}

class AddWorkoutViewModel(private val prefs: UserPreferences) : ViewModel() {

    private val _state = MutableStateFlow<AddWorkoutState>(AddWorkoutState.Idle)
    val state: StateFlow<AddWorkoutState> = _state

    fun createWorkout(req: CreateWorkoutRequest, onUnauthorized: (() -> Unit)? = null) {
        _state.value = AddWorkoutState.Loading
        viewModelScope.launch {
            try {
                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { runBlocking { prefs.getAccessToken() } },
                    onUnauthorized = { onUnauthorized?.invoke() }
                )
                val api = retrofit.create(WorkoutApi::class.java)
                val res: Response<WorkoutLog> = api.createWorkout(req)

                if (res.isSuccessful) {
                    _state.value = AddWorkoutState.Success(res.body())
                } else {
                    _state.value = AddWorkoutState.Error("HTTP ${res.code()} ${res.message()}")
                }
            } catch (e: Exception) {
                _state.value = AddWorkoutState.Error(e.localizedMessage ?: "Błąd")
            }
        }
    }
}
