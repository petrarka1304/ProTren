package com.example.protren.network

import com.example.protren.auth.AuthBus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ApiClient {
    private const val BASE_URL = "https://protren-backend.onrender.com/"
    private const val REFRESH_TIMEOUT_MS = 3000L

    /** Interceptor, który globalnie:
     *  - łapie wyjątki sieciowe i wysyła komunikat do ErrorBus
     *  - dla HTTP 4xx/5xx (poza 401) wysyła komunikat do ErrorBus
     */
    private val globalErrorInterceptor = Interceptor { chain ->
        try {
            val response = chain.proceed(chain.request())

            // 401 obsługujemy osobno (AuthBus + dialog), więc tutaj nie spamujemy snackbarami
            if (!response.isSuccessful && response.code != 401) {
                val body = try { response.peekBody(4096).string() } catch (_: Throwable) { null }
                ErrorBus.emit(ErrorMapper.fromHttp(response.code, body))
            }

            response
        } catch (t: Throwable) {
            // Błąd sieciowy (brak neta, timeout itp.)
            ErrorBus.emit(ErrorMapper.fromNetwork(t))
            throw t
        }
    }

    /** Retrofit bez autoryzacji */
    fun create(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(globalErrorInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun createWithAuth(
        tokenProvider: () -> String?,
        refreshTokenProvider: (() -> String?)? = null,
        onTokensUpdated: ((newAccess: String, newRefresh: String?) -> Unit)? = null,
        onUnauthorized: (() -> Unit)? = null
    ): Retrofit {

        val tokenInterceptor = TokenInterceptor()
        val refreshInProgress = AtomicBoolean(false)
        val mutex = Any()

        val refreshInterceptor = Interceptor { chain ->
            val originalReq = chain.request()
            val resp = chain.proceed(originalReq)

            if (resp.code != 401) return@Interceptor resp

            // brak refreshu => natychmiast wygaszenie sesji
            if (refreshTokenProvider == null || onTokensUpdated == null) {
                resp.close()
                onUnauthorized?.invoke()
                try { AuthBus.emitLoggedOut("Sesja wygasła. Zaloguj się ponownie.") } catch (_: Throwable) {}
                return@Interceptor resp
            }

            // jeżeli refresh już trwa, spróbuj raz z aktualnym tokenem
            if (!refreshInProgress.compareAndSet(false, true)) {
                resp.close()
                return@Interceptor retryOnceWithCurrentToken(chain, originalReq, tokenProvider)
            }

            try {
                synchronized(mutex) {
                    // spróbuj ponowić z obecnym tokenem (może już się odświeżył)
                    resp.close()
                    val tryAgain = retryOnceWithCurrentToken(chain, originalReq, tokenProvider)
                    if (tryAgain.code != 401) return@Interceptor tryAgain
                    tryAgain.close()

                    val refreshToken = refreshTokenProvider.invoke().orEmpty()
                        .removePrefix("Bearer ").removePrefix("bearer ").trim()

                    if (refreshToken.isBlank()) {
                        onUnauthorized?.invoke()
                        try { AuthBus.emitLoggedOut("Sesja wygasła. Zaloguj się ponownie.") } catch (_: Throwable) {}
                        return@Interceptor chain.proceed(originalReq)
                    }

                    val refreshRetrofit = create()
                    val api = refreshRetrofit.create(RefreshApi::class.java)

                    val refreshResponse = runBlocking {
                        try {
                            withTimeout(REFRESH_TIMEOUT_MS) {
                                api.refresh(RefreshRequest(refreshToken))
                            }
                        } catch (_: Throwable) {
                            null
                        }
                    }

                    if (refreshResponse != null &&
                        refreshResponse.isSuccessful &&
                        refreshResponse.body() != null
                    ) {
                        val body = refreshResponse.body()!!
                        onTokensUpdated.invoke(body.accessToken, body.refreshToken)
                        TokenInterceptor.updateToken(body.accessToken)

                        val retried = originalReq.newBuilder()
                            .removeHeader("Authorization")
                            .addHeader("Authorization", "Bearer ${body.accessToken}")
                            .build()

                        return@Interceptor chain.proceed(retried)
                    }

                    // refresh nieudany
                    onUnauthorized?.invoke()
                    try { AuthBus.emitLoggedOut("Sesja wygasła. Zaloguj się ponownie.") } catch (_: Throwable) {}
                    return@Interceptor chain.proceed(originalReq)
                }
            } finally {
                refreshInProgress.set(false)
            }
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(globalErrorInterceptor)
            .addInterceptor(tokenInterceptor)
            .addInterceptor(refreshInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun retryOnceWithCurrentToken(
        chain: Interceptor.Chain,
        originalReq: okhttp3.Request,
        tokenProvider: () -> String?
    ): Response {
        val currentAccess = (tokenProvider() ?: "")
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()

        if (currentAccess.isBlank()) {
            return chain.proceed(originalReq)
        }

        val retried = originalReq.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $currentAccess")
            .build()

        return chain.proceed(retried)
    }
}
