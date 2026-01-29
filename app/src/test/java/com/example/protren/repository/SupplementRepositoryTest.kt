package com.example.protren.repository

import com.example.protren.model.Supplement
import com.example.protren.network.ApiClient
import com.example.protren.network.SupplementApi
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

class SupplementRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: SupplementApi
    private lateinit var repository: SupplementRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()


        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(SupplementApi::class.java)
        repository = SupplementRepository(api)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getAll - powinien poprawnie sparsować JSON z backendu`() = runTest {
        val mockJson = """
            [
              {
                "_id": "65a123456789abc",
                "name": "Kreatyna",
                "dosage": "5g",
                "times": ["morning"],
                "takenLog": []
              },
              {
                "_id": "65b987654321xyz",
                "name": "Omega-3",
                "dosage": "1 tab"
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockJson).setResponseCode(200))
        val response = repository.getAll()
        assertTrue(response.isSuccessful)
        val list = response.body()

        assertEquals(2, list?.size)
        assertEquals("Kreatyna", list?.get(0)?.name)
        assertEquals("65a123456789abc", list?.get(0)?._id)
    }

    @Test
    fun `takeToday - powinien wysłać poprawne żądanie do API`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        repository.take("sup-123")
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/supplements/take/sup-123", request.path)
    }

    @Test
    fun `handleError - powinien obsłużyć błąd 500`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val response = repository.getAll()
        assertTrue(!response.isSuccessful)
        assertEquals(500, response.code())
    }
}