package com.givewp.donationterminal.data.remote.interceptor

import com.givewp.donationterminal.data.local.prefs.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches `Authorization: Bearer <token>` to every request that has a stored JWT. */
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // TokenStorage reads are plain (synchronous) EncryptedSharedPreferences access -- safe to
        // call directly here since OkHttp interceptors always run off the main thread.
        val token = tokenStorage.getToken()
        val authorized = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(authorized)
    }
}
