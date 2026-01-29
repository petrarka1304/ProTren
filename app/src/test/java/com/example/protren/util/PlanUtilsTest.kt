package com.example.protren.util

import com.example.protren.model.TrainingPlan
import com.example.protren.model.TrainingPlanDay
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanUtilsTest {

    private fun createPlan(vararg dayTitles: String): TrainingPlan {
        val days = dayTitles.map { TrainingPlanDay(title = it) }
        return TrainingPlan(id = "1", name = "Test", days = days)
    }

    @Test
    fun `wywnioskujTagPlanu - powinien wykryć Full Body`() {
        val plan = createPlan("Full Body A", "Full Body B")
        assertEquals("Full Body", wywnioskujTagPlanu(plan))
    }

    @Test
    fun `wywnioskujTagPlanu - powinien wykryć Upper Lower`() {
        val plan = createPlan("Góra Siła", "Dół Hipertrofia")
        assertEquals("Upper / Lower", wywnioskujTagPlanu(plan))
    }

    @Test
    fun `wywnioskujTagPlanu - powinien wykryć PPL`() {
        val plan = createPlan("Push", "Pull", "Legs")
        assertEquals("PPL", wywnioskujTagPlanu(plan))
    }

    @Test
    fun `wywnioskujTagPlanu - powinien zwrócić Własny dla innych nazw`() {
        val plan = createPlan("Poniedziałek", "Środa", "Piątek")
        assertEquals("Własny", wywnioskujTagPlanu(plan))
    }
}