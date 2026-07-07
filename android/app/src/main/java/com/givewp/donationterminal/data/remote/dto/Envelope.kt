package com.givewp.donationterminal.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Generic success/error envelope wrapping every backend response body (contract section 1). */
@JsonClass(generateAdapter = true)
data class ApiEnvelope<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: ApiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String,
    @Json(name = "details") val details: Map<String, Any?>? = null
)

/** Concrete (non-generic) envelope used only to parse HTTP error bodies via Moshi reflectively. */
@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "error") val error: ApiErrorDto? = null
)
