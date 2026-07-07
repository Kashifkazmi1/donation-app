package com.givewp.donationterminal.data.remote

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.ApiErrorEnvelope
import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.model.AppResult
import com.squareup.moshi.Moshi
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Wraps a Retrofit call, unwraps the `{success, data, error}` envelope (contract section 1), and
 * maps every failure mode -- HTTP error envelope, malformed body, timeout, no connectivity -- to
 * a domain [AppResult.Failure] so repositories/ViewModels never see Retrofit/OkHttp exceptions.
 */
class NetworkCallExecutor @Inject constructor(private val moshi: Moshi) {

    suspend fun <T> execute(call: suspend () -> Response<ApiEnvelope<T>>): AppResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                when {
                    body?.success == true && body.data != null -> AppResult.Success(body.data)
                    body?.error != null -> AppResult.Failure(
                        AppError(errorTypeForCode(body.error.code), body.error.code, body.error.message)
                    )
                    else -> AppResult.Failure(
                        AppError(AppErrorType.SERVER, "EMPTY_RESPONSE", "Server returned an empty response")
                    )
                }
            } else {
                AppResult.Failure(mapHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: SocketTimeoutException) {
            AppResult.Failure(AppError(AppErrorType.TIMEOUT, "TIMEOUT", "The request timed out. Please try again."))
        } catch (e: UnknownHostException) {
            AppResult.Failure(AppError(AppErrorType.NETWORK, "NO_CONNECTION", "No internet connection."))
        } catch (e: IOException) {
            AppResult.Failure(AppError(AppErrorType.NETWORK, "NETWORK_ERROR", e.message ?: "Network error"))
        } catch (e: Exception) {
            AppResult.Failure(AppError(AppErrorType.UNKNOWN, "UNKNOWN_ERROR", e.message ?: "Unexpected error"))
        }
    }

    private fun mapHttpError(httpCode: Int, rawBody: String?): AppError {
        val parsed = rawBody?.let {
            runCatching { moshi.adapter(ApiErrorEnvelope::class.java).fromJson(it) }.getOrNull()
        }
        val code = parsed?.error?.code ?: "HTTP_$httpCode"
        val message = parsed?.error?.message ?: defaultMessageForHttpCode(httpCode)
        val type = if (parsed?.error?.code != null) errorTypeForCode(code) else httpCodeToType(httpCode)
        return AppError(type, code, message)
    }

    private fun httpCodeToType(httpCode: Int): AppErrorType = when {
        httpCode == 401 -> AppErrorType.AUTH
        httpCode == 400 -> AppErrorType.VALIDATION
        httpCode in 500..599 -> AppErrorType.SERVER
        else -> AppErrorType.UNKNOWN
    }

    private fun errorTypeForCode(code: String): AppErrorType = when (code) {
        "INVALID_CREDENTIALS" -> AppErrorType.AUTH
        "VALIDATION_ERROR" -> AppErrorType.VALIDATION
        "STRIPE_ERROR", "GIVEWP_ERROR" -> AppErrorType.SERVER
        "PAYMENT_NOT_SUCCEEDED", "INTENT_NOT_FOUND" -> AppErrorType.SERVER
        else -> AppErrorType.UNKNOWN
    }

    private fun defaultMessageForHttpCode(httpCode: Int): String = when (httpCode) {
        400 -> "Invalid request."
        401 -> "Your session has expired. Please log in again."
        404 -> "Not found."
        409 -> "This request has already been processed."
        502 -> "Payment provider error. Please try again."
        else -> "Something went wrong (HTTP $httpCode)."
    }
}
