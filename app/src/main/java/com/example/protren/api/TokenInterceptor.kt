package com.example.protren.api

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Interceptor dodający Authorization: Bearer <token> bez blokowania wątków.
 * Token jest przechowywany w pamięci w companion object i aktualizowany po logowaniu.
 */
class TokenInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentToken()?.let(::cleanToken)

        val req = chain.request().newBuilder()
            .removeHeader("Authorization")
            .apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(req)
    }

    private fun cleanToken(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
        return t.ifBlank { null }
    }

    companion object {
        // globalny, thread-safe token w pamięci
        private val tokenRef = AtomicReference<String?>()

        /** Pobiera aktualny ACCESS token (czysty JWT, bez Bearer). */
        fun currentToken(): String? = tokenRef.get()

        /** Ustawia lub aktualizuje ACCESS token. */
        fun updateToken(token: String?) {
            tokenRef.set(token?.removePrefix("Bearer ")
                ?.removePrefix("bearer ")
                ?.trim()
                ?.ifBlank { null })
        }

        /** Czyści token z pamięci. */
        fun clearToken() = tokenRef.set(null)
    }
}
