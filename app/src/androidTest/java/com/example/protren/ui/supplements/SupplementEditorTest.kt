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

        // Klikamy Zapisz bez wpisywania niczego
        composeTestRule.onNodeWithText("Zapisz").performClick()

        // Sprawdzamy czy wyskoczył błąd walidacji
        composeTestRule.onNodeWithText("Podaj nazwę suplementu").assertIsDisplayed()
    }

    @Test
    fun form_acceptsInput() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            SupplementEditorScreen(navController = navController, supplementId = null)
        }

        // 1. Znajdujemy pole po etykiecie i wpisujemy tekst
        // Używamy onNodeWithText dla etykiety, bo to zazwyczaj działa najlepiej do 'kliknięcia' w pole
        composeTestRule.onNodeWithText("Nazwa suplementu").performTextInput("Witamina C")
        composeTestRule.onNodeWithText("Dawkowanie (np. 2000 IU, 1 kaps.)").performTextInput("1000mg")

        // 2. Weryfikujemy czy tekst się wpisał
        // POPRAWKA: Szukamy tekstu "Witamina C", ALE tylko w elemencie edytowalnym (hasSetTextAction).
        // To ignoruje tekst "Witamina C" widoczny na liście podpowiedzi (który powodował błąd).
        composeTestRule.onNode(
            hasText("Witamina C") and hasSetTextAction()
        ).assertIsDisplayed()

        // To samo dla dawkowania
        composeTestRule.onNode(
            hasText("1000mg") and hasSetTextAction()
        ).assertIsDisplayed()
    }
}