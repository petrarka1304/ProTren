package com.example.protren.logic

import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

enum class PlanType { FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS, CUSTOM }
enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }
enum class Equipment { HOME, GYM, MINIMAL }
enum class Goal { STRENGTH, HYPERTROPHY, ENDURANCE, FAT_LOSS }

data class ExerciseDb(
    val name: String,
    val group: String? = null,
    val equipment: String? = null,
    val tags: List<String> = emptyList()
)

data class GenerationOptions(
    val type: PlanType,
    val daysPerWeek: Int,
    val level: Level,
    val equipment: Equipment,
    val goal: Goal,
    val numberOfWeeks: Int = 4
)

data class ExerciseSpec(
    val name: String,
    val sets: Int,
    val reps: IntRange,
    val rir: Int,
    val muscleGroup: String,
    val pattern: String
)

data class DayPlan(
    val title: String,
    val exercises: List<ExerciseSpec>
)

data class GeneratedPlan(
    val microcycles: List<List<DayPlan>>
)

object AutoWorkoutGenerator {

    private fun equipmentMatches(eq: Equipment, equipmentField: String?): Boolean {
        val e = equipmentField?.lowercase().orEmpty()
        return when (eq) {
            Equipment.GYM -> true
            Equipment.HOME -> e.contains("dom") || e.contains("brak") || e.contains("drążek") || e.contains("hantl")
            Equipment.MINIMAL -> e.contains("dom") || e.contains("kettle") || e.contains("hantl") || e.contains("guma")
        }
    }

    private fun weeklySetsBase(goal: Goal, level: Level): Map<String, Int> {
        val base = mutableMapOf(
            "Nogi" to 14, "Klatka" to 10, "Plecy" to 12,
            "Barki" to 8, "Biceps" to 6, "Triceps" to 6, "Core" to 6
        )
        val levelMult = when(level) {
            Level.BEGINNER -> 0.7; Level.INTERMEDIATE -> 1.0; Level.ADVANCED -> 1.3
        }
        base.replaceAll { _, v -> (v * levelMult).toInt().coerceAtLeast(3) }
        return base
    }

    private fun repsAndRir(goal: Goal): Pair<IntRange, Int> = when (goal) {
        Goal.STRENGTH -> 3..6 to 3
        Goal.HYPERTROPHY -> 8..12 to 1
        Goal.ENDURANCE -> 15..20 to 1
        Goal.FAT_LOSS -> 10..15 to 1
    }

    private val patternToGroup = mapOf(
        "squat" to "Nogi",
        "hinge" to "Nogi",
        "single_leg" to "Nogi",
        "push" to "Klatka",
        "pull" to "Plecy",
        "core" to "Core"
    )

    private fun splitTemplate(type: PlanType, days: Int): List<List<String>> {
        val d = days.coerceIn(2, 6)
        return when (type) {
            PlanType.FULL_BODY -> List(d) { listOf("squat", "push", "pull", "hinge", "core") }
            PlanType.UPPER_LOWER -> List(d) { i ->
                if (i % 2 == 0) listOf("push", "pull", "push", "pull", "core")
                else listOf("squat", "hinge", "single_leg", "core")
            }
            PlanType.PUSH_PULL_LEGS -> List(d) { i ->
                when (i % 3) {
                    0 -> listOf("push", "push", "core")
                    1 -> listOf("pull", "pull", "core")
                    else -> listOf("squat", "hinge", "single_leg", "core")
                }
            }
            else -> List(d) { listOf("squat", "push", "pull", "core") }
        }
    }

    private fun pickFromCatalog(
        pattern: String,
        options: GenerationOptions,
        catalog: List<ExerciseDb>,
        rng: Random,
        disallowNames: Set<String>,
        allowedGroups: List<String>?
    ): ExerciseDb? {
        val tag = pattern.lowercase()

        val candidates = catalog.filter {
            equipmentMatches(options.equipment, it.equipment) &&
                    it.name !in disallowNames &&
                    (tag in it.tags.map(String::lowercase)) &&
                    (allowedGroups == null || it.group in allowedGroups)
        }

        if (candidates.isEmpty()) return null

        return if (options.level == Level.BEGINNER) {
            val compounds = candidates.filter { "compound" in it.tags }
            if (compounds.isNotEmpty()) compounds.random(rng) else candidates.random(rng)
        } else {
            candidates.random(rng)
        }
    }

    fun generate(options: GenerationOptions, catalog: List<ExerciseDb>, seed: Long? = null): GeneratedPlan {
        val rng = seed?.let { Random(it) } ?: Random(System.currentTimeMillis())
        val template = splitTemplate(options.type, options.daysPerWeek)
        val (repRange, baseRir) = repsAndRir(options.goal)
        val weeklyBudget = weeklySetsBase(options.goal, options.level)
        val patternCounts = template.flatten().groupingBy { it }.eachCount()

        fun buildWeek(progressFactor: Double): List<DayPlan> {
            return template.mapIndexed { dayIdx, dayPatterns ->
                val dayExercises = mutableListOf<ExerciseSpec>()
                val namesInDay = mutableSetOf<String>()

                val isSplit = options.type != PlanType.FULL_BODY
                val dayIsLower = dayPatterns.any { it in listOf("squat", "hinge", "single_leg") }
                val dayIsUpper = dayPatterns.any { it in listOf("push", "pull") }

                val allowedGroups = if (!isSplit) null else {
                    val list = mutableListOf<String>("Core", "Cardio")
                    if (dayIsLower) list.add("Nogi")
                    if (dayIsUpper) list.addAll(listOf("Klatka", "Plecy", "Barki", "Biceps", "Triceps"))
                    list
                }

                dayPatterns.forEach { p ->
                    val ex = pickFromCatalog(p, options, catalog, rng, namesInDay, allowedGroups)
                    if (ex != null) {
                        val totalSets = weeklyBudget[ex.group] ?: 8
                        val setsToday = ceil(totalSets.toDouble() / (patternCounts[p] ?: 1)).toInt().coerceIn(2, 4)

                        dayExercises += ExerciseSpec(
                            name = ex.name,
                            sets = setsToday,
                            reps = IntRange((repRange.first * progressFactor).toInt(), (repRange.last * progressFactor).toInt()),
                            rir = baseRir,
                            muscleGroup = ex.group ?: "Inne",
                            pattern = p
                        )
                        namesInDay.add(ex.name)
                    }
                }

                val targetCount = if (options.level == Level.BEGINNER) 6 else 8
                var safetyValve = 0
                while (dayExercises.size < targetCount && safetyValve < 15) {
                    safetyValve++
                    val randomPattern = dayPatterns.random(rng)
                    val acc = pickFromCatalog(randomPattern, options, catalog, rng, namesInDay, allowedGroups)

                    if (acc != null) {
                        dayExercises += ExerciseSpec(
                            name = acc.name,
                            sets = 2,
                            reps = IntRange((repRange.first * 1.5).toInt(), (repRange.last * 1.5).toInt()),
                            rir = baseRir + 1,
                            muscleGroup = acc.group ?: "Dodatki",
                            pattern = randomPattern
                        )
                        namesInDay.add(acc.name)
                    }
                }

                DayPlan("Dzień ${dayIdx + 1}", dayExercises)
            }
        }

        val microcycles = (1..options.numberOfWeeks.coerceIn(1, 6)).map { wk ->
            buildWeek(1.0 + (wk - 1) * 0.05)
        }

        return GeneratedPlan(microcycles)
    }
}