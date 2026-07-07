package com.givewp.donationterminal.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConnectionTokenResponseDto(
    @Json(name = "secret") val secret: String
)

@JsonClass(generateAdapter = true)
data class ReaderStatusResponseDto(
    @Json(name = "location") val location: LocationDto,
    @Json(name = "readers") val readers: List<ReaderDto>
)

@JsonClass(generateAdapter = true)
data class LocationDto(
    @Json(name = "id") val id: String,
    @Json(name = "displayName") val displayName: String
)

@JsonClass(generateAdapter = true)
data class ReaderDto(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "serialNumber") val serialNumber: String,
    @Json(name = "deviceType") val deviceType: String,
    @Json(name = "status") val status: String,
    @Json(name = "batteryLevel") val batteryLevel: Float?,
    @Json(name = "locationId") val locationId: String
)
