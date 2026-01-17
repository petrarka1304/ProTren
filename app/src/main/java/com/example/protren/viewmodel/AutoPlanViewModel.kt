package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.protren.data.ExerciseRepository
import com.example.protren.logic.*
import com.example.protren.network.ExerciseRequest
import com.example.protren.network.TrainingPlanApi
import com.example.protren.network.TrainingPlanCreateRequest
import com.example.protren.network.TrainingPlanDayCreateDto
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AutoPlanViewModel(
    private val repo: ExerciseRepository,
    private val planApi: TrainingPlanApi
) : ViewModel() {

    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var options by mutableStateOf(
        GenerationOptions(
            type = PlanType.FULL_BODY,
            daysPerWeek = 3,
            level = Level.BEGINNER,
            equipment = Equipment.GYM,
            goal = Goal.HYPERTROPHY,
            numberOfWeeks = 4 // domyślnie 4 tygodnie
        )
    ); private set

    var plan by mutableStateOf<GeneratedPlan?>(null); private set

    private var catalog: List<ExerciseDb> = emptyList()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.loadAllForGenerator(limitPerPage = 300) }
                .onSuccess { list ->
                    catalog = list
                    loading = false
                }
                .onFailure { e ->
                    loading = false
                    error = e.message ?: "Nieznany błąd"
                }
        }
    }

    fun updateOptions(new: GenerationOptions) {
        options = new
    }

    fun generate() {
        if (catalog.isEmpty()) {
            error = "Brak katalogu ćwiczeń (spróbuj ponownie)."
            return
        }
        plan = AutoWorkoutGenerator.generate(options, catalog)
    }

    fun clearPlan() {
        plan = null
    }

    /** Zapis do backendu – jawne mapowanie na ExerciseRequest */
    suspend fun savePlan(): Pair<Boolean, String> {
        val current = plan ?: return false to "Najpierw wygeneruj plan"

        val body = TrainingPlanCreateRequest(
            name = "Plan ${options.type.readable()} • ${options.daysPerWeek} dni/tydz. • ${options.numberOfWeeks} tygodni",
            isPublic = false,
            days = current.microcycles
                .flatten()
                .map { day ->
                    TrainingPlanDayCreateDto(
                        title = day.title,
                        exercises = day.exercises.map { ex ->
                            ExerciseRequest(
                                name = ex.name,
                                muscleGroup = ex.muscleGroup,
                                sets = ex.sets,
                                repsMin = ex.reps.first,
                                repsMax = ex.reps.last,
                                rir = ex.rir,
                                pattern = ex.pattern          // <── KLUCZOWA LINIA
                            )
                        }
                    )
                }
        )

        return runCatching {
            val resp = planApi.createPlan(body)
            if (resp.isSuccessful) true to "Plan zapisany ✔"
            else false to "Nie udało się zapisać (HTTP ${resp.code()})"
        }.getOrElse { e ->
            false to ("Błąd zapisu: ${e.message ?: "nieznany"}")
        }
    }
}

/* ===== Fabryka ===== */
class AutoPlanVmFactory(
    private val repo: ExerciseRepository,
    private val planApi: TrainingPlanApi
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AutoPlanViewModel(repo, planApi) as T
}

/* ===== Pomocnicze ===== */
private fun PlanType.readable(): String = when (this) {
    PlanType.FULL_BODY      -> "całego ciała"
    PlanType.UPPER_LOWER    -> "góra / dół"
    PlanType.PUSH_PULL_LEGS -> "push / pull / nogi"
    PlanType.CUSTOM         -> "niestandardowy"
}
