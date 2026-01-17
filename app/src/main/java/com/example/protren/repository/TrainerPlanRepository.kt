package com.example.protren.repository

import com.example.protren.model.Supplement
import com.example.protren.network.TrainerPlanApi

/**
 * Repozytorium do suplementów użytkownika po stronie trenera.
 * Zgodne z TrainerPlanApi:
 *  - list:    GET trainer-user-supplements/{traineeId}
 *  - create:  POST trainer-user-supplements/{traineeId}           (Body: Map)
 *  - update:  PATCH trainer-user-supplements/{traineeId}/{id}      (Body: Map)
 *  - delete:  DELETE trainer-user-supplements/{traineeId}/{id}
 *  - take:    POST trainer-user-supplements/{traineeId}/{id}/take-today
 *  - undo:    POST trainer-user-supplements/{traineeId}/{id}/undo-today
 */
class TrainerPlanRepository(
    private val api: TrainerPlanApi
) {
    // ===== LISTA / ODCZYT =====
    suspend fun listSupplements(traineeId: String): List<Supplement> {
        val resp = api.listSupplements(traineeId)
        if (resp.isSuccessful) return resp.body().orEmpty()
        throw RuntimeException("HTTP ${resp.code()}: nie można pobrać suplementów")
    }

    // ===== CREATE / UPDATE / DELETE =====
    suspend fun createSupplement(
        traineeId: String,
        body: Map<String, @JvmSuppressWildcards Any?>
    ): Supplement {
        val resp = api.createSupplement(traineeId, body)
        if (resp.isSuccessful) return resp.body() ?: error("Brak danych w odpowiedzi CREATE")
        throw RuntimeException("HTTP ${resp.code()}: błąd dodawania suplementu")
    }

    suspend fun updateSupplement(
        traineeId: String,
        id: String,
        body: Map<String, @JvmSuppressWildcards Any?>
    ): Supplement {
        val resp = api.updateSupplement(traineeId, id, body)
        if (resp.isSuccessful) return resp.body() ?: error("Brak danych w odpowiedzi UPDATE")
        throw RuntimeException("HTTP ${resp.code()}: błąd edycji suplementu")
    }

    suspend fun deleteSupplement(traineeId: String, id: String) {
        val resp = api.deleteSupplement(traineeId, id)
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}: błąd usuwania suplementu")
    }

    // ===== AKCJE DNIA =====
    suspend fun takeToday(traineeId: String, id: String): Supplement {
        val resp = api.takeToday(traineeId, id)
        if (resp.isSuccessful) return resp.body() ?: error("Brak danych w odpowiedzi TAKE")
        throw RuntimeException("HTTP ${resp.code()}: błąd oznaczania jako wzięty dziś")
    }

    suspend fun undoToday(traineeId: String, id: String): Supplement {
        val resp = api.undoToday(traineeId, id)
        if (resp.isSuccessful) return resp.body() ?: error("Brak danych w odpowiedzi UNDO")
        throw RuntimeException("HTTP ${resp.code()}: błąd cofania oznaczenia na dziś")
    }
}
