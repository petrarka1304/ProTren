package com.example.protren.network

import com.example.protren.api.TokenInterceptor
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
import retrofit2.http.GET

private interface PingApi {
    @GET("/ping")
    suspend fun ping(): retrofit2.Response<Unit>
}

class AuthorizationHeaderIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PingApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        TokenInterceptor.updateToken("abc.jwt")

        val client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor())
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PingApi::class.java)
    }

    @After
    fun tearDown() {
        TokenInterceptor.clearToken()
        server.shutdown()
    }

    @Test
    fun `request zawiera Authorization Bearer`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        api.ping()

        val recorded = server.takeRequest()
        assertEquals("Bearer abc.jwt", recorded.getHeader("Authorization"))
    }
}
