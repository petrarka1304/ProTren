package com.example.protren.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {

    @Test
    fun save_and_read_token() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val prefs = UserPreferences(context)

        prefs.saveToken("token123")
        val token = prefs.getToken()

        assertEquals("token123", token)
    }

    @Test
    fun save_and_read_role() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val prefs = UserPreferences(context)

        prefs.saveRole("trainer")
        val role = prefs.getRole()

        assertEquals("trainer", role)
    }
}
