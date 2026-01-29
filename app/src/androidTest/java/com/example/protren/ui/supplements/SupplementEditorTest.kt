package com.example.protren.ui.supplements

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test

class SupplementEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveButton_showsError_whenNameIsBlank() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            SupplementEditorScreen(
                navController = navController,
                supplementId = null
            )
        }

        composeTestRule.onNodeWithText("Zapisz").performClick()
        composeTestRule.onNodeWithText("Podaj nazwę suplementu").assertIsDisplayed()
    }

    @Test
    fun form_acceptsInput() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            SupplementEditorScreen(navController = navController, supplementId = null)
        }

        composeTestRule.onNodeWithText("Nazwa suplementu").performTextInput("Witamina C")
        composeTestRule.onNodeWithText("Dawkowanie (np. 2000 IU, 1 kaps.)").performTextInput("1000mg")

        composeTestRule.onNode(
            hasText("Witamina C") and hasSetTextAction()
        ).assertIsDisplayed()
        composeTestRule.onNode(
            hasText("1000mg") and hasSetTextAction()
        ).assertIsDisplayed()
    }
}