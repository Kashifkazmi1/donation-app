package com.givewp.donationterminal.data.remote.api

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.CompleteDonationRequestDto
import com.givewp.donationterminal.data.remote.dto.CreatePaymentIntentRequestDto
import com.givewp.donationterminal.data.remote.dto.PaymentIntentResponseDto
import com.givewp.donationterminal.data.remote.dto.TransactionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PaymentsApi {
    @POST("payments/intent")
    suspend fun createPaymentIntent(
        @Body request: CreatePaymentIntentRequestDto
    ): Response<ApiEnvelope<PaymentIntentResponseDto>>

    @POST("donations/complete")
    suspend fun completeDonation(
        @Body request: CompleteDonationRequestDto
    ): Response<ApiEnvelope<TransactionDto>>
}
