package com.example.protren

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.protren.model.Supplement
import com.example.protren.ui.supplements.TodayList
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
            TodayList(
                items = listOf(testItem),
                onToggle = { _, _ -> },
                onEdit = {}
            )
        }

        composeTestRule.onNodeWithText("Witamina C").assertIsDisplayed()
        composeTestRule.onNodeWithText("1000mg", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Rano", substring = true).assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Do wzięcia").performClick()
        assert(clickedId == "55")
        assert(clickedStatus == true)
    }

    @Test
    fun showsTakenStateCorrectly() {
        val testItem = Supplement(
            _id = "2",
            name = "Omega-3",
            takenToday = true
        )

        composeTestRule.setContent {
            TodayList(
                items = listOf(testItem),
                onToggle = { _, _ -> },
                onEdit = {}
            )
        }

        composeTestRule.onNodeWithText("Wzięte").assertIsDisplayed()
    }
}