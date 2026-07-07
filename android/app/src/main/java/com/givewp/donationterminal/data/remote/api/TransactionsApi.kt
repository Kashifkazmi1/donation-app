package com.givewp.donationterminal.data.remote.api

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.TransactionsPageResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TransactionsApi {
    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("status") status: String? = null
    ): Response<ApiEnvelope<TransactionsPageResponseDto>>
}
