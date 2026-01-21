package com.example.protren.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test

class BottomBarNavigationAndroidTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun TestAppWithBottomBar() {
        val navController = rememberNavController()

        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") { Text("SCREEN_HOME") }
                composable("supplements/today") { Text("SCREEN_SUPPLEMENTS_TODAY") }
                composable("chats") { Text("SCREEN_CHATS") }
                composable("analytics") { Text("SCREEN_ANALYTICS") }
                composable("profile") { Text("SCREEN_PROFILE") }
            }

            BottomBar(navController = navController)
        }
    }

    @Test
    fun bottomBar_displays_all_items() {
        rule.setContent { TestAppWithBottomBar() }

        rule.onNodeWithText("Start").assertIsDisplayed()
        rule.onNodeWithText("Suplementy").assertIsDisplayed()
        rule.onNodeWithText("Czaty").assertIsDisplayed()
        rule.onNodeWithText("Analityka").assertIsDisplayed()
        rule.onNodeWithText("Profil").assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigates_to_profile() {
        rule.setContent { TestAppWithBottomBar() }

        // klikamy w element BottomBar po tekście + akcji kliknięcia (stabilniej niż samo onNodeWithText)
        rule.onNode(hasText("Profil") and hasClickAction()).performClick()

        rule.onNodeWithText("SCREEN_PROFILE").assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigates_to_supplements_today() {
        rule.setContent { TestAppWithBottomBar() }

        rule.onNode(hasText("Suplementy") and hasClickAction()).performClick()

        rule.onNodeWithText("SCREEN_SUPPLEMENTS_TODAY").assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigates_to_chats_and_analytics() {
        rule.setContent { TestAppWithBottomBar() }

        rule.onNode(hasText("Czaty") and hasClickAction()).performClick()
        rule.onNodeWithText("SCREEN_CHATS").assertIsDisplayed()

        rule.onNode(hasText("Analityka") and hasClickAction()).performClick()
        rule.onNodeWithText("SCREEN_ANALYTICS").assertIsDisplayed()
    }
}
