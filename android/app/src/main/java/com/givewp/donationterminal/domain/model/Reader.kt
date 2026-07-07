package com.givewp.donationterminal.domain.model

/** A Bluetooth reader discovered on-device via the Stripe Terminal SDK. */
data class DiscoveredReader(
    val serialNumber: String,
    val readerId: String?,
    val label: String?,
    val deviceType: String?,
    val batteryLevel: Float?,
    val locationId: String?
)

enum class ReaderConnectionPhase {
    CHECKING_REQUIREMENTS,
    MISSING_PERMISSIONS,
    BLUETOOTH_DISABLED,
    DISCOVERING,
    NO_READERS_FOUND,
    AWAITING_SELECTION,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ReaderConnectionUiState(
    val phase: ReaderConnectionPhase = ReaderConnectionPhase.CHECKING_REQUIREMENTS,
    val discoveredReaders: List<DiscoveredReader> = emptyList(),
    val connectedReader: DiscoveredReader? = null,
    val batteryLevel: Float? = null,
    val errorMessage: String? = null
)

/** Server-known reader/location info from GET /terminal/reader-status (informational only). */
data class RegisteredReader(
    val id: String,
    val label: String,
    val serialNumber: String,
    val deviceType: String,
    val status: String,
    val batteryLevel: Float?,
    val locationId: String
)

data class TerminalLocation(
    val id: String,
    val displayName: String
)
