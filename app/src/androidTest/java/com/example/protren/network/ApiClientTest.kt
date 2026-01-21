package com.example.protren.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.protren.data.remote.AuthApi
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiClientTest {

    @Test
    fun apiClient_creates_authApi() {
        val api = ApiClient.create().create(AuthApi::class.java)
        assertNotNull(api)
    }
}
