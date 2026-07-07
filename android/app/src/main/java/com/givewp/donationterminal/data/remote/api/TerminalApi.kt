package com.givewp.donationterminal.data.remote.api

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.ConnectionTokenResponseDto
import com.givewp.donationterminal.data.remote.dto.ReaderStatusResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

interface TerminalApi {
    @POST("terminal/connection-token")
    suspend fun createConnectionToken(): Response<ApiEnvelope<ConnectionTokenResponseDto>>

    @GET("terminal/reader-status")
    suspend fun readerStatus(): Response<ApiEnvelope<ReaderStatusResponseDto>>
}
