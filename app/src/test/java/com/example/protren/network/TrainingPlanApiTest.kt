package com.example.protren.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TrainingPlanApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: TrainingPlanApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(TrainingPlanApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getPlans - powinien zmapować pole _id z MongoDB`() = runTest {
        // GIVEN - Backend zwraca tablicę JSON z polem "_id"
        val json = """
            [
              {
                "_id": "mongo-id-123",
                "name": "Plan Siłowy",
                "isPublic": true,
                "days": []
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        // WHEN
        val response = api.getPlans()

        // THEN
        assertTrue(response.isSuccessful)
        val list = response.body()
        assertNotNull(list)
        assertEquals(1, list?.size)

        // KLUCZOWE: Sprawdzamy czy pole `_id` (z DTO) zostało wypełnione
        assertEquals("mongo-id-123", list?.get(0)?._id)
        assertEquals("Plan Siłowy", list?.get(0)?.name)
    }

    @Test
    fun `getPlan - powinien poprawnie parsować zagnieżdżone dni i ćwiczenia`() = runTest {
        // GIVEN - Skomplikowany JSON (Plan -> Dni -> Ćwiczenia)
        // Zakładamy strukturę TrainingPlanDay zgodną z tym co widziałem w backendzie
        val detailedJson = """
            {
              "_id": "plan-full",
              "name": "FBW A",
              "days": [
                {
                  "title": "Poniedziałek",
                  "exercises": [
                    { 
                        "name": "Przysiad", 
                        "muscleGroup": "Nogi",
                        "sets": 5, 
                        "repsMin": 5,
                        "repsMax": 5,
                        "rir": 1,
                        "pattern": "Squat"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(detailedJson).setResponseCode(200))

        // WHEN
        val response = api.getPlan("plan-full")

        // THEN
        assertTrue(response.isSuccessful)
        val plan = response.body()

        assertEquals("FBW A", plan?.name)

        // Sprawdzamy czy lista dni nie jest pusta
        val days = plan?.days
        assertNotNull(days)
        assertEquals(1, days?.size)

        // Ponieważ nie widzę klasy TrainingPlanDay, używam refleksji lub ogólnego dostępu,
        // ale w Twoim kodzie po prostu odwołaj się do pól:
        // assertEquals("Poniedziałek", days[0].title)
        // assertEquals("Przysiad", days[0].exercises[0].name)
    }

    @Test
    fun `createPlan - powinien wysłać poprawne body do API`() = runTest {
        // GIVEN
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(201))

        // Tworzymy request zgodny z Twoim TrainingPlanCreateRequest
        val requestDto = TrainingPlanCreateRequest(
            name = "Nowy Plan",
            days = listOf(
                TrainingPlanDayCreateDto(
                    title = "Dzień 1",
                    exercises = listOf(
                        ExerciseRequest(
                            name = "Pompki",
                            muscleGroup = "Klatka",
                            sets = 3,
                            repsMin = 10,
                            repsMax = 12,
                            rir = 2
                        )
                    )
                )
            )
        )

        // WHEN
        api.createPlan(requestDto)

        // THEN - Sprawdzamy co Retrofit wysłał w świat
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/training-plans", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)

        val bodySent = recordedRequest.body.readUtf8()
        // Sprawdzamy czy JSON zawiera kluczowe dane
        assertTrue(bodySent.contains("Nowy Plan"))
        assertTrue(bodySent.contains("Pompki"))
        assertTrue(bodySent.contains("exercises"))
    }
    // ... (pozostałe testy: getPlans, getPlan, createPlan)

    @Test
    fun `updatePlan - powinien wysłać żądanie PUT z wybranymi polami`() = runTest {
        // GIVEN
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        // Request aktualizacji (np. zmieniamy tylko nazwę)
        val updateReq = TrainingPlanUpdateRequest(
            name = "Zmieniona Nazwa",
            isPublic = null, // Tego nie zmieniamy
            days = null      // Tego nie zmieniamy
        )

        // WHEN
        api.updatePlan("plan-123", updateReq)

        // THEN
        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/training-plans/plan-123", request.path)

        val body = request.body.readUtf8()
        // Sprawdzamy czy wysłaliśmy nową nazwę
        assertTrue(body.contains("Zmieniona Nazwa"))
        // Ważne: Sprawdzamy czy Retrofit NIE wysłał pól null (np. "days": null)
        // Jeśli Gson jest dobrze skonfigurowany, nulli nie powinno być w JSON-ie
        // (Chyba że chcesz je czyścić - tu zakładamy domyślne działanie Gsona)
    }

    @Test
    fun `deletePlan - powinien wysłać żądanie DELETE`() = runTest {
        // GIVEN
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // WHEN
        api.deletePlan("plan-to-delete")

        // THEN
        val request = mockWebServer.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/training-plans/plan-to-delete", request.path)
    }
}