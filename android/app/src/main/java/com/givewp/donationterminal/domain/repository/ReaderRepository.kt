package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DiscoveredReader
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.RegisteredReader
import com.givewp.donationterminal.domain.model.TerminalLocation
import kotlinx.coroutines.flow.Flow

enum class ReaderConnectionStatus { NOT_CONNECTED, CONNECTING, CONNECTED }

/**
 * Thin abstraction over the Stripe Terminal SDK so that ViewModels never touch
 * `com.stripe.stripeterminal.*` types directly (the SDK requires an Activity/Application
 * context in places, and this indirection keeps ViewModels unit-testable with a fake).
 */
interface ReaderRepository {
    val connectionStatus: Flow<ReaderConnectionStatus>
    val batteryLevel: Flow<Float?>
    val discoveredReaders: Flow<List<DiscoveredReader>>
    val unexpectedDisconnect: Flow<Unit>

    fun isBluetoothEnabled(): Boolean

    fun hasRequiredPermissions(): Boolean

    fun requiredPermissions(): Array<String>

    suspend fun startDiscovery(): AppResult<Unit>

    fun stopDiscovery()

    suspend fun connect(reader: DiscoveredReader, locationId: String): AppResult<DiscoveredReader>

    suspend fun disconnect()

    /**
     * Drives collectPaymentMethod + confirmPaymentIntent (or processPayment) using the client
     * secret returned by POST /payments/intent. Returns the Stripe PaymentIntent id on success
     * so the caller can call POST /donations/complete.
     */
    suspend fun collectAndConfirmPayment(clientSecret: String): AppResult<PaymentCollectionResult>

    fun cancelCollectPaymentMethod()

    /** GET /terminal/reader-status -- informational, server-registered readers/location. */
    suspend fun fetchRegisteredReaders(): AppResult<Pair<TerminalLocation, List<RegisteredReader>>>
}
