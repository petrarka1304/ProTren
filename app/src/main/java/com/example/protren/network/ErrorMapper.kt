package com.example.protren.network

import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {


    fun fromNetwork(t: Throwable): String {
        return when (t) {
            is UnknownHostException -> "Brak połączenia z internetem."
            is SocketTimeoutException -> "Przekroczono czas oczekiwania. Spróbuj ponownie."
            is IOException -> "Problem z połączeniem. Sprawdź internet i spróbuj ponownie."
            else -> "Wystąpił błąd połączenia."
        }
    }

    fun fromHttp(code: Int, errorBody: String?): String {
        val serverMsg = extractServerMessage(errorBody)
        if (!serverMsg.isNullOrBlank()) return serverMsg

        return when (code) {
            400 -> "Nieprawidłowe dane. Sprawdź formularz."
            401 -> "Sesja wygasła. Zaloguj się ponownie."
            403 -> "Brak uprawnień do wykonania tej akcji."
            404 -> "Nie znaleziono zasobu (404)."
            in 500..599 -> "Błąd serwera. Spróbuj ponownie później."
            else -> "Wystąpił błąd (HTTP $code)."
        }
    }

    private fun extractServerMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            when {
                json.has("message") -> json.optString("message").takeIf { it.isNotBlank() }
                json.has("msg") -> json.optString("msg").takeIf { it.isNotBlank() }
                json.has("error") -> json.optString("error").takeIf { it.isNotBlank() }
                json.has("detail") -> json.optString("detail").takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
