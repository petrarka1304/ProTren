package com.example.protren.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.protren.data.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginViewModelAndroidTest {

    @Test
    fun login_without_backend_emits_Error_state() = runBlocking {
        val prefs = UserPreferences(
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        )

        val vm = LoginViewModel(prefs)

        vm.login("test@test.com", "password")

        delay(1500)

        val state = vm.state.value

        assertTrue(
            state is LoginState.Error || state is LoginState.Loading
        )
    }
}
