package com.example.protren.repository

import com.example.protren.network.ExerciseApi
import com.example.protren.network.ExerciseDto
import com.example.protren.network.ExercisePageDto
import com.example.protren.network.GroupDto
import com.example.protren.network.CreateExerciseRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ExerciseRepositoryTest {

    private class FakeExerciseApi(
        private val pages: List<List<ExerciseDto>>
    ) : ExerciseApi {

        override suspend fun getExercises(
            query: String?,
            group: String?,
            equipment: String?,
            page: Int,
            limit: Int,
            mine: Boolean?
        ): Response<ExercisePageDto> {
            val idx = page - 1
            val items = pages.getOrNull(idx).orEmpty()
            val body = ExercisePageDto(
                page = page,
                limit = limit,
                total = pages.sumOf { it.size },
                items = items
            )
            return Response.success(body)
        }

        override suspend fun getGroups(): Response<List<GroupDto>> =
            Response.success(emptyList())

        override suspend fun createExercise(body: CreateExerciseRequest) =
            Response.success(
                ExerciseDto(_id = "x", name = body.name, group = body.group, equipment = body.equipment, tags = body.tags)
            )

        override suspend fun deleteExercise(id: String): Response<Unit> =
            Response.success(Unit)
    }

    @Test
    fun `loadAllForGenerator pobiera kolejne strony i usuwa duplikaty po nazwie`() = runBlocking {
        val page1 = listOf(
            ExerciseDto(_id = "1", name = "Pompki"),
            ExerciseDto(_id = "2", name = "Przysiad")
        )
        val page2 = listOf(
            ExerciseDto(_id = "3", name = "pompki"),   // duplikat różniący się wielkością liter
            ExerciseDto(_id = "4", name = "Martwy ciąg")
        )
        val page3 = emptyList<ExerciseDto>() // warunek końca (items.size < limit)

        val api = FakeExerciseApi(pages = listOf(page1, page2, page3))
        val repo = ExerciseRepository(api)

        val result = repo.loadAllForGenerator(limitPerPage = 2, maxPages = 10)

        val names = result.map { it.name }.sorted()
        assertEquals(listOf("Martwy ciąg", "Pompki", "Przysiad").sorted(), names)
    }
}
