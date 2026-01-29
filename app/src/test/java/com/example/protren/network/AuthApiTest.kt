package com.example.protren.network // lub com.example.protren.data.remote

import com.example.protren.data.remote.AuthApi
import com.example.protren.data.remote.LoginRequest
import com.example.protren.data.remote.RegisterRequest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(AuthApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `login - powinien wysłać poprawne żądanie POST i sparsować odpowiedź`() = runTest {
        val jsonResponse = """
            {
                "token": "old-token",
                "accessToken": "new-access-token",
                "refreshToken": "new-refresh-token",
                "user": {
                    "id": "123",
                    "email": "test@test.pl",
                    "role": "user"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val request = LoginRequest("test@test.pl", "Haslo123")
        val response = api.login(request)
        assertTrue(response.isSuccessful)
        assertEquals("new-access-token", response.body()?.accessToken)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/auth/login", recordedRequest.path)
    }

    @Test
    fun `register - powinien wysłać dane rejestracji`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val request = RegisterRequest("nowy@test.pl", "Haslo123")
        val response = api.register(request)

        assertTrue(response.isSuccessful)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/auth/register", recordedRequest.path)
        assertTrue(recordedRequest.body.readUtf8().contains("nowy@test.pl"))
    }
}