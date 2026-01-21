package com.example.protren.data.remote
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthApiMockWebServerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(AuthApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login - wysyla poprawny JSON i parsuje odpowiedz`() = runBlocking {
        val json = """
            {
              "token": "Bearer abc",
              "accessToken": "abc",
              "refreshToken": "ref",
              "user": { "id": "1", "email": "a@b.com", "role": "user" }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val res = api.login(LoginRequest(email = "a@b.com", password = "pass123"))

        assertTrue(res.isSuccessful)
        val body = res.body()
        assertNotNull(body)
        assertEquals("Bearer abc", body!!.token)
        assertEquals("abc", body.accessToken)
        assertEquals("ref", body.refreshToken)
        assertEquals("user", body.user.role)

        val recorded = server.takeRequest()
        assertEquals("/api/auth/login", recorded.path)

        val sentBody = recorded.body.readUtf8()
        assertTrue(sentBody.contains("\"email\":\"a@b.com\""))
        assertTrue(sentBody.contains("\"password\":\"pass123\""))
    }
}
