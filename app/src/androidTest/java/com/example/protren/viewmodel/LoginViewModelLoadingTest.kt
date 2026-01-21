package com.example.protren.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.protren.data.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginViewModelLoadingTest {

    @Test
    fun login_sets_Loading_state_immediately() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val prefs = UserPreferences(context)
        val vm = LoginViewModel(prefs)

        vm.login("a@b.com", "password")

        val state = vm.state.value

        assertTrue(state is LoginState.Loading)
    }
}
