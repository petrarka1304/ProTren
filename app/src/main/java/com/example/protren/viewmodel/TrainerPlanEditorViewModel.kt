package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.ExerciseRequest
import com.example.protren.network.TrainingPlanApi
import com.example.protren.network.TrainingPlanDayCreateDto
import com.example.protren.network.TrainingPlanDayDto
import com.example.protren.network.TrainingPlanDto
import com.example.protren.network.TrainingPlanUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ExerciseUi(
    val id: String,
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Float = 0f
)

data class DayUi(
    val title: String,
    val exercises: List<ExerciseUi> = emptyList(),
    val existingExercisesCount: Int = 0
) {
    val exercisesCount: Int
        get() = if (exercises.isNotEmpty()) exercises.size else existingExercisesCount
}

sealed class PlanEditorState {
    object Loading : PlanEditorState()
    data class Loaded(
        val id: String,
        val name: String,
        val days: List<DayUi>,
        val isPublic: Boolean
    ) : PlanEditorState()

    data class Error(val message: String) : PlanEditorState()
}

class TrainerPlanEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private fun api(): TrainingPlanApi? {
        val token = prefs.getAccessToken()
        if (token.isNullOrBlank()) return null

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    addHeader("Authorization", "Bearer $token")
                }.build()
                chain.proceed(req)
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(TrainingPlanApi::class.java)
    }

    private val _state = MutableStateFlow<PlanEditorState>(PlanEditorState.Loading)
    val state: StateFlow<PlanEditorState> = _state

    fun load(id: String) {
        viewModelScope.launch {
            _state.value = PlanEditorState.Loading
            try {
                val api = api() ?: run {
                    _state.value = PlanEditorState.Error("Brak tokena – zaloguj się ponownie.")
                    return@launch
                }
                val res = api.getPlan(id)
                if (!res.isSuccessful) {
                    _state.value = PlanEditorState.Error("HTTP ${res.code()}")
                    return@launch
                }

                val dto: TrainingPlanDto = res.body()
                    ?: return@launch run {
                        _state.value = PlanEditorState.Error("Brak danych planu")
                    }

                val daysUi = dto.days.map { day ->
                    val exercisesUi = (day.exercises ?: emptyList()).mapIndexed { idx, ex ->
                        ExerciseUi(
                            id = idx.toString(),
                            name = ex.name ?: "Ćwiczenie ${idx + 1}",
                            sets = 3,
                            reps = 10,
                            weight = 0f
                        )
                    }

                    DayUi(
                        title = day.title,
                        exercises = exercisesUi,
                        existingExercisesCount = exercisesUi.size
                    )
                }

                _state.value = PlanEditorState.Loaded(
                    id = dto._id,
                    name = dto.name,
                    days = daysUi,
                    isPublic = dto.isPublic
                )
            } catch (e: Exception) {
                _state.value =
                    PlanEditorState.Error(e.localizedMessage ?: "Błąd podczas pobierania planu")
            }
        }
    }

    fun rename(newName: String) {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        _state.value = current.copy(name = newName)
    }

    fun addDay() {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        _state.value = current.copy(days = current.days + DayUi(title = "Nowy dzień"))
    }

    fun removeDay(index: Int) {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        if (index !in current.days.indices) return
        val list = current.days.toMutableList()
        list.removeAt(index)
        _state.value = current.copy(days = list)
    }

    fun renameDay(index: Int, title: String) {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        if (index !in current.days.indices) return
        val list = current.days.toMutableList()
        val old = list[index]
        list[index] = old.copy(title = title)
        _state.value = current.copy(days = list)
    }

    fun setExercisesForDay(index: Int, ids: List<String>, names: List<String>) {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        if (index !in current.days.indices) return

        val list = current.days.toMutableList()
        val exercises = ids.mapIndexed { idx, id ->
            ExerciseUi(
                id = id,
                name = names.getOrNull(idx) ?: "Ćwiczenie ${idx + 1}",
                sets = 3,
                reps = 10,
                weight = 0f
            )
        }
        val old = list[index]
        list[index] = old.copy(
            exercises = exercises,
            existingExercisesCount = exercises.size
        )
        _state.value = current.copy(days = list)
    }

    fun updateExerciseInDay(dayIndex: Int, exIndex: Int, updated: ExerciseUi) {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        if (dayIndex !in current.days.indices) return
        val days = current.days.toMutableList()
        val day = days[dayIndex]
        if (exIndex !in day.exercises.indices) return

        val exList = day.exercises.toMutableList()
        exList[exIndex] = updated
        days[dayIndex] = day.copy(exercises = exList, existingExercisesCount = exList.size)
        _state.value = current.copy(days = days)
    }

    fun reload() {
        val current = _state.value as? PlanEditorState.Loaded ?: return
        load(current.id)
    }

    fun save(cb: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val current = _state.value as? PlanEditorState.Loaded
            if (current == null) {
                cb(false, "Brak danych do zapisu")
                return@launch
            }

            try {
                val api = api()
                if (api == null) {
                    cb(false, "Brak tokena – zaloguj się ponownie.")
                    return@launch
                }

                val dayDtos: List<TrainingPlanDayCreateDto> = current.days.map { dayUi ->
                    val exercises: List<ExerciseRequest> =
                        if (dayUi.exercises.isNotEmpty()) {
                            dayUi.exercises.map { ex ->
                                val safeName = ex.name.trim().ifBlank { "Ćwiczenie" }
                                val safeSets = ex.sets.coerceAtLeast(1)
                                val safeReps = ex.reps.coerceAtLeast(1)

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
                        } else {
                            emptyList()
                        }

                    TrainingPlanDayCreateDto(
                        title = dayUi.title.trim().ifBlank { "Dzień" },
                        exercises = exercises
                    )
                }

                val body = TrainingPlanUpdateRequest(
                    name = current.name.trim(),
                    days = dayDtos,
                    isPublic = current.isPublic
                )


                val res = api.updatePlan(current.id, body)
                if (res.isSuccessful) {
                    cb(true, "Zapisano zmiany")
                } else {
                    cb(false, "HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                cb(false, e.localizedMessage ?: "Błąd podczas zapisywania planu")
            }
        }
    }
}
