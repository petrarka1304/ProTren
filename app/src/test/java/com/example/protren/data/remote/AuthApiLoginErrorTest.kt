package com.example.protren.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthApiLoginErrorTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login - gdy backend zwraca 401 to response ma kod 401`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"Invalid credentials"}""")
        )

        val res = api.login(LoginRequest(email = "a@b.com", password = "bad"))

        assertEquals(false, res.isSuccessful)
        assertEquals(401, res.code())
    }
}
