package com.example.protren

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.protren.model.Supplement
import com.example.protren.ui.supplements.TodayList // Musisz zmienić TodayList na public, żeby test widział
import org.junit.Rule
import org.junit.Test

class TodayListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsSupplementDataCorrectly() {
        // GIVEN
        val testItem = Supplement(
            _id = "1",
            name = "Witamina C",
            dosage = "1000mg",
            times = listOf("morning"),
            takenToday = false
        )

        // WHEN
        composeTestRule.setContent {
            // Uwaga: Jeśli TodayList jest 'private' w Twoim pliku,
            // musisz usunąć słowo 'private' w kodzie SupplementsScreen.kt
            TodayList(
                items = listOf(testItem),
                onToggle = { _, _ -> },
                onEdit = {}
            )
        }

        // THEN
        composeTestRule.onNodeWithText("Witamina C").assertIsDisplayed()
        composeTestRule.onNodeWithText("1000mg", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Rano", substring = true).assertIsDisplayed()
        // Przycisk "Do wzięcia" powinien być widoczny
        composeTestRule.onNodeWithText("Do wzięcia").assertIsDisplayed()
    }

    @Test
    fun clickingToggleTriggersCallback() {
        var clickedId: String? = null
        var clickedStatus: Boolean? = null

        val testItem = Supplement(
            _id = "55",
            name = "Magnez",
            takenToday = false
        )

        composeTestRule.setContent {
            TodayList(
                items = listOf(testItem),
                onToggle = { id, status ->
                    clickedId = id
                    clickedStatus = status
                },
                onEdit = {}
            )
        }

        // Kliknij przycisk "Do wzięcia"
        composeTestRule.onNodeWithText("Do wzięcia").performClick()

        // Sprawdź czy callback zadziałał
        assert(clickedId == "55")
        assert(clickedStatus == true)
    }

    @Test
    fun showsTakenStateCorrectly() {
        val testItem = Supplement(
            _id = "2",
            name = "Omega-3",
            takenToday = true // Wzięte
        )

        composeTestRule.setContent {
            TodayList(
                items = listOf(testItem),
                onToggle = { _, _ -> },
                onEdit = {}
            )
        }

        // Powinien widzieć przycisk "Wzięte" i ikonę Check
        composeTestRule.onNodeWithText("Wzięte").assertIsDisplayed()
    }
}