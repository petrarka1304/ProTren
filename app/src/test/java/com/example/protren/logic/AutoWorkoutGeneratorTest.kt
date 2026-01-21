package com.example.protren.logic

import org.junit.Assert.*
import org.junit.Test

class AutoWorkoutGeneratorTest {

    private fun sampleCatalog(): List<ExerciseDb> = listOf(
        ExerciseDb("Przysiad ze sztangą", equipment = "siłownia", tags = listOf("squat")),
        ExerciseDb("Martwy ciąg", equipment = "siłownia", tags = listOf("hinge")),
        ExerciseDb("Wyciskanie leżąc", equipment = "siłownia", tags = listOf("push")),
        ExerciseDb("Wiosłowanie sztangą", equipment = "siłownia", tags = listOf("pull")),
        ExerciseDb("Plank", equipment = "brak", tags = listOf("core")),
        ExerciseDb("Wykroki", equipment = "brak", tags = listOf("single_leg")),
        ExerciseDb("Hip thrust", equipment = "siłownia", tags = listOf("hinge")),
        ExerciseDb("Pompki", equipment = "brak", tags = listOf("push")),
        ExerciseDb("Podciąganie", equipment = "drążek", tags = listOf("pull")),
        ExerciseDb("Brzuszki", equipment = "brak", tags = listOf("core")),
        ExerciseDb("Bułgarski przysiad", equipment = "brak", tags = listOf("single_leg")),
        ExerciseDb("Front squat", equipment = "siłownia", tags = listOf("squat")),
    )

    @Test
    fun `generator tworzy co najmniej 8 cwiczen na dzien i bez duplikatow w dniu`() {
        val options = GenerationOptions(
            type = PlanType.FULL_BODY,
            daysPerWeek = 3,
            level = Level.BEGINNER,
            equipment = Equipment.GYM,
            goal = Goal.HYPERTROPHY,
            numberOfWeeks = 1
        )

        val plan = AutoWorkoutGenerator.generate(
            options = options,
            catalog = sampleCatalog(),
            seed = 123L
        )

        val week = plan.microcycles.single()
        assertEquals(3, week.size)

        week.forEach { day ->
            assertTrue("Za mało ćwiczeń w dniu", day.exercises.size >= 8)
            val names = day.exercises.map { it.name }
            assertEquals("Duplikaty ćwiczeń w obrębie dnia", names.distinct().size, names.size)
        }
    }
}
