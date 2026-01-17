package com.example.protren.network

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path

// Odpowiedź z backendu: redirectUri do PayU + opcjonalne orderId
data class CreateTrainerOrderResponse(
    val redirectUri: String? = null,
    val orderId: String? = null
)

interface PaymentApi {

    // POST /api/payments/payu/trainer/{trainerId}
    @POST("api/payments/payu/trainer/{trainerId}")
    suspend fun createTrainerOrder(
        @Path("trainerId") trainerId: String
    ): Response<CreateTrainerOrderResponse>
}
