package com.example.protren.repository

import com.example.protren.logic.ExerciseDb
import com.example.protren.network.CreateExerciseRequest
import com.example.protren.network.ExerciseApi
import com.example.protren.network.ExerciseDto
import com.example.protren.network.ExercisePageDto
import com.example.protren.network.GroupDto

class ExerciseRepository(private val api: ExerciseApi) {

    suspend fun page(
        query: String?,
        group: String?,
        equipment: String?,
        page: Int,
        limit: Int = 50,
        mine: Boolean = false
    ): ExercisePageDto? =
        api.getExercises(query, group, equipment, page, limit, mine).body()

    suspend fun groups(): List<GroupDto> = api.getGroups().body().orEmpty()

    suspend fun create(
        name: String,
        group: String?,
        equipment: String?,
        tags: List<String>
    ): ExerciseDto? =
        api.createExercise(CreateExerciseRequest(name, group, equipment, tags)).body()

    suspend fun delete(id: String): Boolean = api.deleteExercise(id).isSuccessful

    suspend fun loadAllForGenerator(
        limitPerPage: Int = 200,
        maxPages: Int = 100,
        query: String? = null,
        group: String? = null,
        equipment: String? = null,
        mine: Boolean? = null
    ): List<ExerciseDb> {
        val out = mutableListOf<ExerciseDb>()
        var page = 1
        while (true) {
            val resp = api.getExercises(
                query = query,
                group = group,
                equipment = equipment,
                page = page,
                limit = limitPerPage,
                mine = mine
            )
            if (!resp.isSuccessful) break
            val body = resp.body() ?: break
            val items = body.items.map {
                ExerciseDb(
                    name = it.name,
                    group = it.group,
                    equipment = it.equipment,
                    tags = (it.tags ?: emptyList())
                )
            }
            out += items
            if (items.size < limitPerPage) break
            page += 1
            if (page > maxPages) break
        }
        return out.distinctBy { it.name.lowercase() }
    }
}
