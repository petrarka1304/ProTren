package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.UserProfile
import com.example.protren.model.WorkoutLog
import com.example.protren.network.ApiClient
import com.example.protren.network.UserProfileApi
import com.example.protren.network.WorkoutApi
import com.example.protren.network.SupplementApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

sealed class DashboardUIState {
    data object Loading : DashboardUIState()
    data class Error(val message: String) : DashboardUIState()
    data class Success(
        val userName: String = "",
        val profile: UserProfile? = null,
        val lastWorkout: WorkoutLog? = null,
        val todayWorkout: WorkoutLog? = null,
        val totalSets: Int = 0,
        val totalReps: Int = 0,
        val totalVolume: Int = 0,
        val todaySupplementsCount: Int = 0
    ) : DashboardUIState()
}

class HomeViewModel(
    private val prefs: UserPreferences
) : ViewModel() {

    private val _dashboardState = MutableStateFlow<DashboardUIState>(DashboardUIState.Loading)
    val dashboardState: StateFlow<DashboardUIState> = _dashboardState

    fun loadDashboardData() {
        _dashboardState.value = DashboardUIState.Loading
        viewModelScope.launch {
            try {
                val token = prefs.getAccessToken()
                if (token.isNullOrBlank()) {
                    _dashboardState.value =
                        DashboardUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                    return@launch
                }

                val retrofit = ApiClient.createWithAuth(
                    tokenProvider = { token },
                    onUnauthorized = {
                        _dashboardState.value =
                            DashboardUIState.Error("Sesja wygasła – zaloguj się ponownie.")
                    }
                )

                val workoutApi = retrofit.create(WorkoutApi::class.java)
                val profileApi = retrofit.create(UserProfileApi::class.java)
                val supplementApi = retrofit.create(SupplementApi::class.java)

                val userName = runCatching {
                    val res = workoutApi.getUser()
                    if (res.isSuccessful) {
                        res.body()?.email ?: ""
                    } else ""
                }.getOrDefault("")

                val profile = runCatching {
                    val res = profileApi.getProfile()
                    if (res.isSuccessful) res.body() else null
                }.getOrNull()

                val weekly = runCatching {
                    val res = workoutApi.getWeeklySummary()
                    if (res.isSuccessful) res.body() else null
                }.getOrNull()

                val lastWorkout = runCatching {
                    val res = workoutApi.getWorkoutLogs()
                    if (res.isSuccessful) {
                        res.body()?.firstOrNull()
                    } else null
                }.getOrNull()

                val todayWorkout = runCatching {
                    val res = workoutApi.getTodayWorkout()
                    if (res.isSuccessful) res.body() else null
                }.getOrNull()

                val todaySupplementsCount = runCatching {
                    val res = supplementApi.getToday()
                    if (res.isSuccessful) {
                        res.body()?.size ?: 0
                    } else 0
                }.getOrDefault(0)

                _dashboardState.value = DashboardUIState.Success(
                    userName = userName,
                    profile = profile,
                    lastWorkout = lastWorkout,
                    todayWorkout = todayWorkout,
                    totalSets = weekly?.totalSets ?: 0,
                    totalReps = weekly?.totalReps ?: 0,
                    totalVolume = weekly?.totalVolume ?: 0,
                    todaySupplementsCount = todaySupplementsCount
                )
            } catch (e: Exception) {
                _dashboardState.value =
                    DashboardUIState.Error(e.localizedMessage ?: "Błąd ładowania danych")
            }
        }
    }

    private fun Response<*>.errorMsg(): String {
        return try {
            val body = errorBody()?.string()
            if (body.isNullOrBlank()) message()
            else JSONObject(body).optString("msg", message())
        } catch (_: Exception) {
            message()
        }
    }
}
