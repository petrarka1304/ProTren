package com.example.protren.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.protren.data.UserPreferences
import com.example.protren.model.Exercise
import com.example.protren.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class DayPlanUi(
    val date: LocalDate,
    val exercises: List<Exercise> = emptyList(),
    val notes: String = ""
)

private data class DayDraft(
    val exercises: MutableList<Exercise> = mutableListOf(),
    var notes: String = ""
)

sealed interface PeriodPlanState {
    data object Idle : PeriodPlanState
    data object Loading : PeriodPlanState
    data class Error(val message: String) : PeriodPlanState
    data class Ready(
        val start: LocalDate,
        val weeks: Int,
        val days: List<DayPlanUi>
    ) : PeriodPlanState
}

class TrainerPeriodPlanViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)
    private val fmt = DateTimeFormatter.ISO_DATE
    private val draftsByDate = mutableMapOf<LocalDate, DayDraft>()

    private fun trainingPlanApi(): TrainingPlanApi {
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { newAccess, newRefresh ->
                prefs.saveToken(newAccess)
                if (!newRefresh.isNullOrBlank()) prefs.saveRefresh(newRefresh)
            },
            onUnauthorized = { prefs.clearTokens() }
        )
        return retrofit.create(TrainingPlanApi::class.java)
    }

    private fun trainerAdminApi(): TrainerAdminApi {
        val retrofit = ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { newAccess, newRefresh ->
                prefs.saveToken(newAccess)
                if (!newRefresh.isNullOrBlank()) prefs.saveRefresh(newRefresh)
            },
            onUnauthorized = { prefs.clearTokens() }
        )
        return retrofit.create(TrainerAdminApi::class.java)
    }

    private val _state = MutableStateFlow<PeriodPlanState>(PeriodPlanState.Idle)
    val state: StateFlow<PeriodPlanState> = _state

    init {
        val defaultStart = LocalDate.now().withDayOfMonth(1)
        val defaultWeeks = 4
        buildCalendar(defaultStart, defaultWeeks, force = true)
    }

    private fun ensureDraft(date: LocalDate): DayDraft =
        draftsByDate.getOrPut(date) { DayDraft() }

    private fun buildUiDays(startDate: LocalDate, weeks: Int): List<DayPlanUi> {
        val calendarStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val totalDays = weeks * 7

        return (0 until totalDays).map { i ->
            val date = calendarStart.plusDays(i.toLong())
            val draft = draftsByDate[date]
            DayPlanUi(
                date = date,
                exercises = draft?.exercises?.toList().orEmpty(),
                notes = draft?.notes.orEmpty()
            )
        }
    }

    fun buildCalendar(startDate: LocalDate, weeks: Int, force: Boolean = false) {
        val safeWeeks = weeks.coerceIn(1, 26)
        val current = _state.value

        if (!force && current is PeriodPlanState.Ready) {
            if (current.start == startDate && current.weeks == safeWeeks) return
        }

        _state.value = PeriodPlanState.Ready(
            start = startDate,
            weeks = safeWeeks,
            days = buildUiDays(startDate, safeWeeks)
        )
    }

    private inline fun mutateDay(date: LocalDate, mutation: (DayDraft) -> Unit) {
        val s = _state.value as? PeriodPlanState.Ready ?: return
        val draft = ensureDraft(date)
        mutation(draft)

        _state.value = s.copy(days = buildUiDays(s.start, s.weeks))
    }

    fun addExercise(date: LocalDate, ex: Exercise) =
        mutateDay(date) { d -> d.exercises.add(ex) }

    fun addExercises(date: LocalDate, exs: List<Exercise>) =
        mutateDay(date) { d -> d.exercises.addAll(exs) }

    fun removeExercise(date: LocalDate, index: Int) =
        mutateDay(date) { d ->
            if (index in d.exercises.indices) d.exercises.removeAt(index)
        }

    fun updateExercise(date: LocalDate, index: Int, ex: Exercise) =
        mutateDay(date) { d ->
            if (index in d.exercises.indices) d.exercises[index] = ex
        }

    fun updateDayNotes(date: LocalDate, notes: String) =
        mutateDay(date) { d -> d.notes = notes }

    fun saveAsTrainerTemplate(planName: String, onDone: (Boolean, String) -> Unit) {
        val s = _state.value as? PeriodPlanState.Ready
        if (s == null) {
            onDone(false, "Brak danych planu")
            return
        }

        viewModelScope.launch {
            try {
                val api = trainingPlanApi()
                val body = TrainingPlanCreateRequest(
                    name = planName.ifBlank { "Plan ${UUID.randomUUID().toString().take(8)}" },
                    days = s.days.map { d ->
                        TrainingPlanDayCreateDto(
                            title = d.date.format(fmt),
                            exercises = d.exercises.map { e ->
                                ExerciseRequest(
                                    name = (e.name ?: "Ćwiczenie").trim().ifBlank { "Ćwiczenie" },
                                    muscleGroup = "General",
                                    sets = e.sets ?: 3,
                                    repsMin = e.reps ?: 10,
                                    repsMax = e.reps ?: 10,
                                    rir = 2,
                                    pattern = "straight"
                                )
                            }
                        )
                    },
                    isPublic = false
                )
                val res = api.createPlan(body)
                onDone(
                    res.isSuccessful,
                    if (res.isSuccessful) "Zapisano szablon planu" else "Błąd: HTTP ${res.code()}"
                )
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Błąd zapisu")
            }
        }
    }

    fun assignToTrainee(traineeId: String, planName: String, onDone: (Boolean, String) -> Unit) {
        val s = _state.value as? PeriodPlanState.Ready
        if (s == null) {
            onDone(false, "Brak danych planu")
            return
        }

        viewModelScope.launch {
            try {
                val api = trainerAdminApi()
                val body = TrainerCreatePlanRequest(
                    name = planName.ifBlank { "Plan ${UUID.randomUUID().toString().take(8)}" },
                    days = s.days.map { d ->
                        TrainingPlanDayCreateDto(
                            title = d.date.format(fmt),
                            exercises = d.exercises.map { e ->
                                val safeName = (e.name ?: "Ćwiczenie").trim().ifBlank { "Ćwiczenie" }
                                val safeSets = (e.sets ?: 3).coerceAtLeast(1)
                                val safeReps = (e.reps ?: 10).coerceAtLeast(1)

                                ExerciseRequest(
                                    name = safeName,
                                    muscleGroup = "General",
                                    sets = safeSets,
                                    repsMin = safeReps,
                                    repsMax = safeReps,
                                    rir = 2,
                                    pattern = "straight"
                                )
                            }
                        )
                    },
                    isPublic = false
                )

                val res = api.createPlanForUser(traineeId, body)
                onDone(
                    res.isSuccessful,
                    if (res.isSuccessful) "Plan przypisany podopiecznemu" else "Błąd: HTTP ${res.code()}"
                )
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Błąd zapisu")
            }
        }
    }
}
