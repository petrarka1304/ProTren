package com.example.protren.logic

import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/**
 * Generator planów oparty o katalog ćwiczeń z bazy (tagi + sprzęt).
 *
 * Wymagania po stronie bazy:
 *  - każde ćwiczenie może mieć: name, group, equipment, tags:[]
 *  - w seedzie/tagach używamy m.in.: "squat","hinge","push","pull","single_leg","core","cardio"
 *  - equipment zawiera np.: "Siłownia", "Dom", "Siłownia/Dom", "Drążek", "Brak", "Maszyna", "Sztanga", "Hantle"
 */

enum class PlanType { FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS, CUSTOM }
enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }
enum class Equipment { HOME, GYM, MINIMAL }
enum class Goal { STRENGTH, HYPERTROPHY, ENDURANCE, FAT_LOSS }

/** Minimalna reprezentacja ćwiczenia z bazy do generatora. */
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
    val numberOfWeeks: Int = 4 // NOWOŚĆ – liczba tygodni (1–6), domyślnie 4
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

    /* --- nowość: celowana liczba ćwiczeń na dzień --- */
    private const val MIN_EXERCISES_PER_DAY = 8       // ustaw ~7–9; domyślnie 8
    private const val MAX_ACCESSORIES_PER_DAY = 5      // bezpieczny limit, by nie kręcić się w pętli

    /* ---------- mapowanie sprzętu na słowa kluczowe z bazy ---------- */
    private fun equipmentMatches(eq: Equipment, equipmentField: String?): Boolean {
        val e = equipmentField?.lowercase().orEmpty()
        return when (eq) {
            Equipment.GYM     -> e.contains("siłownia") || e.contains("maszyna") || e.contains("sztanga") || e.contains("drążek")
            Equipment.HOME    -> e.contains("dom") || e.contains("brak") || e.contains("drążek") || e.contains("hantl")
            Equipment.MINIMAL -> e.contains("brak") || e.contains("dom") || e.contains("kettlebell") || e.contains("hantl")
        }
    }

    /* pattern -> główna grupa do liczenia objętości */
    private val patternToGroup = mapOf(
        "squat" to "nogi",
        "hinge" to "tył nóg/pośladki",
        "single_leg" to "nogi",
        "push" to "klatka/barki/triceps",
        "pull" to "plecy/biceps",
        "core" to "core",
        "conditioning" to "cardio"
    )

    /* bazowa objętość tygodniowa wg celu/poziomu */
    private fun weeklySetsBase(goal: Goal, level: Level): Map<String, Int> {
        val base = mutableMapOf(
            "nogi" to 12, "tył nóg/pośladki" to 12,
            "klatka/barki/triceps" to 12, "plecy/biceps" to 12,
            "core" to 6
        )
        when (goal) {
            Goal.STRENGTH -> base.replaceAll { _, v -> (v * 0.8).toInt().coerceAtLeast(6) }
            Goal.ENDURANCE -> base.replaceAll { _, v -> (v * 0.7).toInt().coerceAtLeast(5) }
            Goal.FAT_LOSS -> {
                base.replaceAll { _, v -> (v * 0.9).toInt().coerceAtLeast(6) }
                base["conditioning"] = 3
            }
            Goal.HYPERTROPHY -> { /* domyślna objętość */ }
        }
        when (level) {
            Level.BEGINNER     -> base.replaceAll { _, v -> max(6, (v * 0.75).toInt()) }
            Level.INTERMEDIATE -> {}
            Level.ADVANCED     -> base.replaceAll { _, v -> (v * 1.2).toInt() }
        }
        return base
    }

    /* zakres powtórzeń i bazowy RIR wg celu */
    private fun repsAndRir(goal: Goal): Pair<IntRange, Int> = when (goal) {
        Goal.STRENGTH     -> 4..6  to 2
        Goal.HYPERTROPHY  -> 6..12 to 1
        Goal.ENDURANCE    -> 12..20 to 1
        Goal.FAT_LOSS     -> 8..15  to 1
    }

    /* ---------- SZABLONY SPLITÓW ---------- */
    private fun splitTemplate(type: PlanType, days: Int): List<List<String>> {
        val d = days.coerceIn(2, 6)

        fun fbw(): List<List<String>> =
            List(d) { listOf("squat", "hinge", "push", "pull", "core") }

        fun upperLower(): List<List<String>> {
            val upper = listOf("push", "pull", "core")
            val lower = listOf("squat", "hinge", "single_leg", "core")
            return List(d) { index -> if (index % 2 == 0) upper else lower }
        }

        fun ppl(): List<List<String>> {
            val push = listOf("push", "core")
            val pull = listOf("pull", "core")
            val legs = listOf("squat", "hinge", "single_leg", "core")
            return List(d) { index ->
                when (index % 3) {
                    0 -> push
                    1 -> pull
                    else -> legs
                }
            }
        }

        return when (type) {
            PlanType.FULL_BODY      -> fbw()
            PlanType.UPPER_LOWER    -> upperLower()
            PlanType.PUSH_PULL_LEGS -> ppl()
            PlanType.CUSTOM         -> fbw()
        }
    }

    /* ---------- wybór ćwiczenia z katalogu ---------- */
    private fun pickFromCatalog(
        pattern: String,
        eq: Equipment,
        catalog: List<ExerciseDb>,
        rng: Random,
        disallowNames: Set<String> = emptySet()
    ): String {
        val tag = pattern.lowercase()

        fun List<ExerciseDb>.avoidDupes() = this.filter { it.name !in disallowNames }

        // 1) preferuj dopasowanie tagu + sprzętu
        val p1 = catalog.filter {
            tag in it.tags.map(String::lowercase) && equipmentMatches(eq, it.equipment)
        }.avoidDupes()
        if (p1.isNotEmpty()) return p1.random(rng).name

        // 2) jeśli brak – ignoruj sprzęt (tag wystarczy)
        val p2 = catalog.filter { tag in it.tags.map(String::lowercase) }.avoidDupes()
        if (p2.isNotEmpty()) return p2.random(rng).name

        // 3) fallback – heurystyka po nazwie
        val heuristics = when (tag) {
            "squat"       -> listOf("przysiad")
            "hinge"       -> listOf("martwy", "rdl", "hip thrust", "ciąg")
            "push"        -> listOf("wycisk", "pompki", "dipy", "barki")
            "pull"        -> listOf("wiosł", "podciąg", "ściąganie")
            "single_leg"  -> listOf("wykrok", "bułgarski", "step-up", "split")
            "core"        -> listOf("plank", "brzuch", "pallof", "allah")
            "conditioning"-> listOf("rower", "bieżnia", "skakanka", "burpees")
            else          -> emptyList()
        }
        val p3 = catalog.filter { e -> heuristics.any { key -> e.name.lowercase().contains(key) } }.avoidDupes()
        if (p3.isNotEmpty()) return p3.random(rng).name

        // 4) ostatecznie zwróć sam pattern (nie powinno się zdarzyć)
        return pattern
    }

    /* ---------- GENERACJA ---------- */
    fun generate(
        options: GenerationOptions,
        catalog: List<ExerciseDb>,
        seed: Long? = null
    ): GeneratedPlan {
        val rng = seed?.let { Random(it) } ?: Random(System.currentTimeMillis())
        val template: List<List<String>> = splitTemplate(options.type, options.daysPerWeek)
        val (repRange, baseRir) = repsAndRir(options.goal)
        val weeklyBudget = weeklySetsBase(options.goal, options.level).toMutableMap()

        val daysPerGroup = mutableMapOf<String, Int>().withDefault { 0 }
        template.forEach { dayPatterns ->
            dayPatterns.forEach { pattern ->
                val group = patternToGroup[pattern] ?: return@forEach
                daysPerGroup[group] = (daysPerGroup.getValue(group) + 1)
            }
        }

        fun buildWeek(progressFactor: Double, rirDelta: Int): List<DayPlan> {
            val remaining = weeklyBudget.toMutableMap()
            val out = mutableListOf<DayPlan>()

            template.forEachIndexed { idx, patterns ->
                val exList = mutableListOf<ExerciseSpec>()
                val title = "Dzień ${idx + 1}"

                // --- główne ćwiczenia wg szablonu ---
                patterns.forEach { p ->
                    val group = patternToGroup[p] ?: return@forEach
                    val total = weeklyBudget[group] ?: 0
                    val dcount = daysPerGroup[group] ?: 1
                    val rawSets = ceil(total.toDouble() / dcount).toInt().coerceAtLeast(2)
                    val setsToday = minOf(rawSets, (remaining[group] ?: 0)).coerceAtLeast(1)

                    if (setsToday > 0) {
                        val name = pickFromCatalog(
                            p,
                            options.equipment,
                            catalog,
                            rng,
                            exList.map { it.name }.toSet()
                        )
                        val reps = IntRange(
                            (repRange.first * progressFactor).toInt().coerceAtLeast(3),
                            (repRange.last  * progressFactor).toInt()
                        )
                        exList += ExerciseSpec(
                            name = name,
                            sets = setsToday,
                            reps = reps,
                            rir = (baseRir - rirDelta).coerceAtLeast(0),
                            muscleGroup = group,
                            pattern = p
                        )
                        remaining[group] = (remaining[group] ?: 0) - setsToday
                    }
                }

                // --- sortowanie głównych (ciężkie -> lekkie) ---
                val orderedMain = exList.sortedBy {
                    when (it.pattern) {
                        "squat","hinge" -> 0
                        "push","pull"   -> 1
                        "single_leg"    -> 2
                        "core"          -> 3
                        else            -> 4
                    }
                }.toMutableList()

                // --- akcesoria, aby dobić do 7–9 ćwiczeń ---
                if (orderedMain.size < MIN_EXERCISES_PER_DAY) {
                    val accessoriesPriority = buildList {
                        // najpierw to, co jest w danym dniu
                        addAll(patterns)
                        // potem uzupełnienia: upper/lower/core
                        addAll(listOf("push","pull","single_leg","core","hinge","squat"))
                    }

                    var added = 0
                    var tries = 0
                    val nameBlocklist = orderedMain.map { it.name }.toMutableSet()

                    while (
                        orderedMain.size < MIN_EXERCISES_PER_DAY &&
                        added < MAX_ACCESSORIES_PER_DAY &&
                        tries < 40
                    ) {
                        tries++
                        val pickPattern = accessoriesPriority.random(rng)
                        val group = patternToGroup[pickPattern] ?: continue
                        val accName = pickFromCatalog(
                            pickPattern,
                            options.equipment,
                            catalog,
                            rng,
                            nameBlocklist
                        )
                        if (accName in nameBlocklist) continue

                        // Akcesoria: 2 serie, delikatnie "lżejszy" RIR
                        val accRir = (baseRir + 1 - rirDelta).coerceAtLeast(0)
                        val accReps = IntRange(
                            max(3, (repRange.first * progressFactor).toInt()),
                            (repRange.last * progressFactor).toInt()
                        )

                        orderedMain += ExerciseSpec(
                            name = accName,
                            sets = 2,
                            reps = accReps,
                            rir = accRir,
                            muscleGroup = group,
                            pattern = pickPattern
                        )
                        nameBlocklist += accName
                        added++
                    }
                }

                out += DayPlan(title = title, exercises = orderedMain)
            }

            return out
        }

        val weeks = mutableListOf<List<DayPlan>>()
        val totalWeeks = options.numberOfWeeks.coerceIn(1, 6)

        // Liniowa progresja: każdy tydzień +5% objętości/zakresu powtórzeń względem poprzedniego
        (1..totalWeeks).forEach { wk ->
            val factor = 1.0 + (wk - 1) * 0.05
            weeks += buildWeek(
                progressFactor = factor,
                rirDelta = if (options.goal == Goal.STRENGTH) 1 else 0
            )
        }

        return GeneratedPlan(microcycles = weeks)
    }
}
