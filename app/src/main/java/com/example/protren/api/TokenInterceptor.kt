package com.example.protren.api

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

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
        private val tokenRef = AtomicReference<String?>()
        fun currentToken(): String? = tokenRef.get()
        fun updateToken(token: String?) {
            tokenRef.set(token?.removePrefix("Bearer ")
                ?.removePrefix("bearer ")
                ?.trim()
                ?.ifBlank { null })
        }

        fun clearToken() = tokenRef.set(null)
    }
}
