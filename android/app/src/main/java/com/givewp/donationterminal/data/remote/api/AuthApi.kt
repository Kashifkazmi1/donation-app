package com.givewp.donationterminal.data.remote.api

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.LoginRequestDto
import com.givewp.donationterminal.data.remote.dto.LoginResponseDto
import com.givewp.donationterminal.data.remote.dto.LogoutResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<ApiEnvelope<LoginResponseDto>>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiEnvelope<LogoutResponseDto>>
}
