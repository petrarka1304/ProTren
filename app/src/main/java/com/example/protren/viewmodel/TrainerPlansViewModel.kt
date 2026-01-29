package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class TrainerPlanItem(
    val id: String,
    val name: String,
    val daysCount: Int
)

class TrainerPlansViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private fun authClient(): OkHttpClient {
        val token = prefs.getAccessToken()
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(req)
            })
            .build()
    }

    private fun plansApi(): TrainingPlanApi =
        Retrofit.Builder()
            .baseUrl("https://protren-backend.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(authClient())
            .build()
            .create(TrainingPlanApi::class.java)

    private fun trainerApi(): TrainerAdminApi =
        Retrofit.Builder()
            .baseUrl("https://protren-backend.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(authClient())
            .build()
            .create(TrainerAdminApi::class.java)

    private val _plans = MutableStateFlow<List<TrainerPlanItem>>(emptyList())
    val plans: StateFlow<List<TrainerPlanItem>> = _plans

    private val _trainees = MutableStateFlow<List<TraineeItem>>(emptyList())
    val trainees: StateFlow<List<TraineeItem>> = _trainees

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = plansApi().getPlans()
                if (res.isSuccessful) {
                    _plans.value = res.body().orEmpty().map {
                        TrainerPlanItem(
                            id = it._id,
                            name = it.name,
                            daysCount = it.days.size
                        )
                    }
                } else {
                    _error.value = "HTTP ${res.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _loading.value = false
        }
    }

    fun loadTrainees() {
        viewModelScope.launch {
            try {
                val res = trainerApi().listTrainees()
                if (res.isSuccessful) {
                    _trainees.value = res.body().orEmpty()
                } else {
                    _trainees.value = emptyList()
                }
            } catch (e: Exception) {
                _trainees.value = emptyList()
            }
        }
    }

    fun delete(id: String, cb: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = plansApi().deletePlan(id)
                if (res.isSuccessful) {
                    cb(true, "Plan usunięty")
                    load()
                } else {
                    cb(false, "Błąd ${res.code()}")
                }
            } catch (e: Exception) {
                cb(false, e.localizedMessage ?: "Błąd")
            }
        }
    }

    fun create(name: String, cb: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = plansApi().createPlan(
                    TrainingPlanCreateRequest(
                        name = name.trim(),
                        days = emptyList()
                    )
                )
                if (res.isSuccessful) {
                    cb(true, "Plan utworzony")
                    load()
                } else {
                    cb(false, "Błąd ${res.code()}")
                }
            } catch (e: Exception) {
                cb(false, e.localizedMessage ?: "Błąd podczas tworzenia planu")
            }
        }
    }

    fun assignPlanToClient(planId: String, clientId: String, cb: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val planRes = plansApi().getPlan(planId)
                if (!planRes.isSuccessful) {
                    cb(false, "Nie udało się pobrać planu")
                    return@launch
                }

                val plan = planRes.body() ?: run {
                    cb(false, "Brak danych planu")
                    return@launch
                }

                val dayDtos: List<TrainingPlanDayCreateDto> = plan.days.map { day ->
                    TrainingPlanDayCreateDto(
                        title = day.title.trim().ifBlank { "Dzień" },
                        exercises = day.exercises.mapIndexed { idx, ex ->
                            val safeName = (ex.name ?: "Ćwiczenie ${idx + 1}").trim().ifBlank { "Ćwiczenie ${idx + 1}" }
                            val safeSets = (ex.sets ?: 3).coerceAtLeast(1)
                            val safeReps = ((ex.repsMax ?: ex.repsMin) ?: 10).coerceAtLeast(1)
                            ExerciseRequest(
                                name = safeName,
                                muscleGroup = "Ogólne",
                                sets = safeSets,
                                repsMin = safeReps,
                                repsMax = safeReps,
                                rir = 2,
                                pattern = "straight"
                            )
                        }
                    )
                }

                val body = TrainerCreatePlanRequest(
                    name = plan.name.trim(),
                    days = dayDtos,
                    isPublic = false
                )


                val res = trainerApi().createPlanForUser(clientId, body)
                if (res.isSuccessful) {
                    cb(true, "Plan przypisany podopiecznemu")
                } else {
                    cb(false, "Błąd ${res.code()}")
                }

            } catch (e: Exception) {
                cb(false, e.localizedMessage ?: "Błąd przypisywania")
            }
        }
    }
}
