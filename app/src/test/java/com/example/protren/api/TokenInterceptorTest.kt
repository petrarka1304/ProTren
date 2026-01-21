package com.example.protren.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        TokenInterceptor.clearToken()
    }

    @After
    fun tearDown() {
        TokenInterceptor.clearToken()
        server.shutdown()
    }

    @Test
    fun `gdy brak tokenu - nie dodaje Authorization`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor())
            .build()

        val req = Request.Builder()
            .url(server.url("/ping"))
            .build()

        client.newCall(req).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `gdy token ustawiony - dodaje Authorization Bearer i czyści prefix`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        TokenInterceptor.updateToken("Bearer  abc.def.ghi  ")

        val client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor())
            .build()

        val req = Request.Builder()
            .url(server.url("/ping"))
            .build()

        client.newCall(req).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer abc.def.ghi", recorded.getHeader("Authorization"))
    }

    @Test
    fun `usuwa istniejący Authorization i nadpisuje nowym`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        TokenInterceptor.updateToken("new.jwt.token")

        val client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor())
            .build()

        val req = Request.Builder()
            .url(server.url("/ping"))
            .addHeader("Authorization", "Bearer old.token")
            .build()

        client.newCall(req).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer new.jwt.token", recorded.getHeader("Authorization"))
    }
}
