package com.example.protren.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.example.protren.ui.login.LoginScreen
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginScreen_shows_basic_fields_and_button() {
        rule.setContent {
            val nav = rememberNavController()
            LoginScreen(navController = nav)
        }

        rule.onNodeWithText("Witaj w ProTren").assertIsDisplayed()
        rule.onNodeWithText("Email").assertIsDisplayed()
        rule.onNodeWithText("Hasło").assertIsDisplayed()
        rule.onNodeWithText("Zaloguj się").assertIsDisplayed()
        rule.onNodeWithText("Nie pamiętasz hasła?").assertIsDisplayed()
    }
}
