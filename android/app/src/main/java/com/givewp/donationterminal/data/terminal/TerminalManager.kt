package com.givewp.donationterminal.data.terminal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.TerminalApi
import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DiscoveredReader
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.RegisteredReader
import com.givewp.donationterminal.domain.model.TerminalLocation
import com.givewp.donationterminal.domain.repository.ReaderConnectionStatus
import com.givewp.donationterminal.domain.repository.ReaderRepository
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.BluetoothReaderListener
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.BatteryStatus
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.log.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Thin, testable wrapper around the Stripe Terminal Android SDK (`com.stripe:stripeterminal-core`).
 * ViewModels depend only on [ReaderRepository] -- never on `com.stripe.stripeterminal.*` types
 * directly -- both for unit-testability and because the SDK needs an Activity/Application
 * context in places that a plain ViewModel shouldn't reach for directly.
 *
 * NOTE ON SDK VERSION: written against the stripeterminal-core 3.x API surface (Terminal
 * singleton initialized once via [Terminal.initTerminal], per-reader-type
 * [DiscoveryConfiguration]/[ConnectionConfiguration] subtypes, callback-based
 * collect/confirm PaymentIntent flow). If the pinned SDK version in
 * gradle/libs.versions.toml drifts, re-check these call sites against that version's docs --
 * this file could not be compiled in the sandbox this project was authored in (no Android SDK /
 * Stripe Maven access), so treat it as reviewed-but-unverified by a real compiler.
 */
@Singleton
class TerminalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val terminalApi: TerminalApi,
    private val networkCallExecutor: NetworkCallExecutor,
    private val connectionTokenProviderProvider: Provider<ConnectionTokenProviderImpl>
) : ReaderRepository {

    private val initMutex = Mutex()
    private var discoveryCancelable: Cancelable? = null
    private var collectCancelable: Cancelable? = null
    private var lastDiscoveredSdkReaders: List<Reader> = emptyList()

    private val _connectionStatus = MutableStateFlow(ReaderConnectionStatus.NOT_CONNECTED)
    override val connectionStatus: Flow<ReaderConnectionStatus> = _connectionStatus.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Float?>(null)
    override val batteryLevel: Flow<Float?> = _batteryLevel.asStateFlow()

    private val _discoveredReaders = MutableStateFlow<List<DiscoveredReader>>(emptyList())
    override val discoveredReaders: Flow<List<DiscoveredReader>> = _discoveredReaders.asStateFlow()

    private val _unexpectedDisconnect = MutableStateFlow(0)
    override val unexpectedDisconnect: Flow<Unit> = callbackFlow {
        // Re-emitted as a distinct event stream; collectors should treat every element as "now".
        var last = _unexpectedDisconnect.value
        val job = kotlinx.coroutines.GlobalScope.launch {
            _unexpectedDisconnect.asStateFlow().collect { value ->
                if (value != last) {
                    last = value
                    trySend(Unit)
                }
            }
        }
        awaitClose { job.cancel() }
    }

    private val terminalListener = object : TerminalListener {
        override fun onConnectionStatusChange(status: ConnectionStatus) {
            _connectionStatus.value = when (status) {
                ConnectionStatus.CONNECTED -> ReaderConnectionStatus.CONNECTED
                ConnectionStatus.CONNECTING -> ReaderConnectionStatus.CONNECTING
                else -> ReaderConnectionStatus.NOT_CONNECTED
            }
        }

        override fun onPaymentStatusChange(status: PaymentStatus) {
            // Exposed for future UI (e.g. a live "waiting for card" indicator); the payment
            // ViewModel currently drives its own state machine from the collect/confirm calls.
        }

        override fun onUnexpectedReaderDisconnect(reader: Reader) {
            _connectionStatus.value = ReaderConnectionStatus.NOT_CONNECTED
            _batteryLevel.value = null
            _unexpectedDisconnect.value += 1
        }
    }

    private val bluetoothReaderListener = object : BluetoothReaderListener {
        override fun onBatteryLevelUpdate(batteryLevel: Float, isCharging: Boolean, status: BatteryStatus) {
            _batteryLevel.value = batteryLevel
        }

        override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
            // Firmware update progress -- not surfaced in this app's UI, no user action needed.
        }
    }

    private fun ensureInitialized() {
        if (Terminal.isInitialized()) return
        Terminal.initTerminal(
            context.applicationContext,
            LogLevel.NONE,
            connectionTokenProviderProvider.get(),
            terminalListener
        )
    }

    // ---------------------------------------------------------------------------------------
    // Requirements
    // ---------------------------------------------------------------------------------------

    override fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        return adapter?.isEnabled == true
    }

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------------------------------------------------------------------------------
    // Discovery / connect
    // ---------------------------------------------------------------------------------------

    override suspend fun startDiscovery(): AppResult<Unit> {
        initMutex.withLock { ensureInitialized() }
        stopDiscovery()

        val config = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(isSimulated = false)

        return suspendCancellableCoroutine { continuation ->
            val discoveryListener = DiscoveryListener { readers ->
                lastDiscoveredSdkReaders = readers
                _discoveredReaders.value = readers.map { it.toDomain() }
            }

            discoveryCancelable = Terminal.getInstance().discoverReaders(
                config,
                discoveryListener,
                object : Callback {
                    override fun onSuccess() {
                        // Discovery process ended (e.g. cancelled). Nothing further to resolve if
                        // the coroutine already returned control after starting the scan.
                    }

                    override fun onFailure(e: TerminalException) {
                        if (continuation.isActive) {
                            continuation.resume(AppResult.Failure(e.toAppError()))
                        }
                    }
                }
            )

            // discoverReaders() streams results via discoveryListener indefinitely until
            // cancelled; treat "scan started successfully" as the async result for the caller,
            // who observes discoveredReaders as a Flow for the actual list.
            if (continuation.isActive) {
                continuation.resume(AppResult.Success(Unit))
            }

            continuation.invokeOnCancellation { stopDiscovery() }
        }
    }

    override fun stopDiscovery() {
        discoveryCancelable?.cancel(object : Callback {
            override fun onSuccess() = Unit
            override fun onFailure(e: TerminalException) = Unit
        })
        discoveryCancelable = null
    }

    override suspend fun connect(reader: DiscoveredReader, locationId: String): AppResult<DiscoveredReader> {
        val sdkReader = lastDiscoveredSdkReaders.firstOrNull { it.serialNumber == reader.serialNumber }
            ?: return AppResult.Failure(
                AppError(AppErrorType.UNKNOWN, "READER_NOT_FOUND", "Reader is no longer available. Please scan again.")
            )

        stopDiscovery()
        _connectionStatus.value = ReaderConnectionStatus.CONNECTING

        val connectionConfig = ConnectionConfiguration.BluetoothConnectionConfiguration(
            locationId = locationId,
            autoReconnectOnUnexpectedDisconnect = true,
            bluetoothReaderReconnectionListener = null
        )

        return suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().connectBluetoothReader(
                sdkReader,
                connectionConfig,
                bluetoothReaderListener,
                object : ReaderCallback {
                    override fun onSuccess(connectedReader: Reader) {
                        _connectionStatus.value = ReaderConnectionStatus.CONNECTED
                        _batteryLevel.value = connectedReader.batteryLevel
                        if (continuation.isActive) {
                            continuation.resume(AppResult.Success(connectedReader.toDomain()))
                        }
                    }

                    override fun onFailure(e: TerminalException) {
                        _connectionStatus.value = ReaderConnectionStatus.NOT_CONNECTED
                        if (continuation.isActive) {
                            continuation.resume(AppResult.Failure(e.toAppError()))
                        }
                    }
                }
            )
        }
    }

    override suspend fun disconnect() {
        if (!Terminal.isInitialized()) return
        suspendCancellableCoroutine<Unit> { continuation ->
            Terminal.getInstance().disconnectReader(object : Callback {
                override fun onSuccess() {
                    _connectionStatus.value = ReaderConnectionStatus.NOT_CONNECTED
                    _batteryLevel.value = null
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onFailure(e: TerminalException) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------
    // Payment collection
    // ---------------------------------------------------------------------------------------

    override suspend fun collectAndConfirmPayment(clientSecret: String): AppResult<PaymentCollectionResult> {
        val retrieved = retrievePaymentIntent(clientSecret)
        if (retrieved is AppResult.Failure) return retrieved
        val intentAfterRetrieve = (retrieved as AppResult.Success).data

        val collected = collectPaymentMethod(intentAfterRetrieve)
        if (collected is AppResult.Failure) return collected
        val intentAfterCollect = (collected as AppResult.Success).data

        val confirmed = confirmPaymentIntent(intentAfterCollect)
        if (confirmed is AppResult.Failure) return confirmed
        val finalIntent = (confirmed as AppResult.Success).data

        if (finalIntent.status != PaymentIntentStatus.SUCCEEDED) {
            return AppResult.Failure(
                AppError(
                    AppErrorType.STRIPE_DECLINED,
                    "PAYMENT_NOT_SUCCEEDED",
                    "The payment was not completed (status: ${finalIntent.status})."
                )
            )
        }

        val charge = finalIntent.getCharges().firstOrNull()
        val cardPresentDetails = charge?.paymentMethodDetails?.cardPresentDetails
        return AppResult.Success(
            PaymentCollectionResult(
                paymentIntentId = finalIntent.id ?: intentAfterRetrieve.id.orEmpty(),
                cardBrand = cardPresentDetails?.brand,
                cardLast4 = cardPresentDetails?.last4
            )
        )
    }

    private suspend fun retrievePaymentIntent(clientSecret: String): AppResult<PaymentIntent> =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().retrievePaymentIntent(
                clientSecret,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        if (continuation.isActive) continuation.resume(AppResult.Success(paymentIntent))
                    }

                    override fun onFailure(e: TerminalException) {
                        if (continuation.isActive) continuation.resume(AppResult.Failure(e.toAppError()))
                    }
                }
            )
        }

    private suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): AppResult<PaymentIntent> =
        suspendCancellableCoroutine { continuation ->
            collectCancelable = Terminal.getInstance().collectPaymentMethod(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        collectCancelable = null
                        if (continuation.isActive) continuation.resume(AppResult.Success(paymentIntent))
                    }

                    override fun onFailure(e: TerminalException) {
                        collectCancelable = null
                        if (continuation.isActive) continuation.resume(AppResult.Failure(e.toAppError()))
                    }
                }
            )
            continuation.invokeOnCancellation { cancelCollectPaymentMethod() }
        }

    private suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): AppResult<PaymentIntent> =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().confirmPaymentIntent(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        if (continuation.isActive) continuation.resume(AppResult.Success(paymentIntent))
                    }

                    override fun onFailure(e: TerminalException) {
                        if (continuation.isActive) continuation.resume(AppResult.Failure(e.toAppError()))
                    }
                }
            )
        }

    override fun cancelCollectPaymentMethod() {
        collectCancelable?.cancel(object : Callback {
            override fun onSuccess() = Unit
            override fun onFailure(e: TerminalException) = Unit
        })
        collectCancelable = null
    }

    // ---------------------------------------------------------------------------------------
    // Server-registered readers (informational)
    // ---------------------------------------------------------------------------------------

    override suspend fun fetchRegisteredReaders(): AppResult<Pair<TerminalLocation, List<RegisteredReader>>> {
        return when (val result = networkCallExecutor.execute { terminalApi.readerStatus() }) {
            is AppResult.Success -> AppResult.Success(
                TerminalLocation(result.data.location.id, result.data.location.displayName) to
                    result.data.readers.map {
                        RegisteredReader(
                            id = it.id,
                            label = it.label,
                            serialNumber = it.serialNumber,
                            deviceType = it.deviceType,
                            status = it.status,
                            batteryLevel = it.batteryLevel,
                            locationId = it.locationId
                        )
                    }
            )
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    private fun Reader.toDomain(): DiscoveredReader = DiscoveredReader(
        serialNumber = serialNumber.orEmpty(),
        readerId = id,
        label = label ?: deviceType?.name,
        deviceType = deviceType?.name,
        batteryLevel = batteryLevel,
        locationId = location?.id
    )

    private fun TerminalException.toAppError(): AppError {
        val type = when (errorCode) {
            TerminalException.TerminalErrorCode.CANCELED -> AppErrorType.STRIPE_CANCELLED
            TerminalException.TerminalErrorCode.DECLINED_BY_READER,
            TerminalException.TerminalErrorCode.DECLINED_BY_STRIPE_API -> AppErrorType.STRIPE_DECLINED
            TerminalException.TerminalErrorCode.BLUETOOTH_DISABLED,
            TerminalException.TerminalErrorCode.BLUETOOTH_SCAN_TIMED_OUT -> AppErrorType.BLUETOOTH_DISABLED
            TerminalException.TerminalErrorCode.BLUETOOTH_PERMISSION_DENIED,
            TerminalException.TerminalErrorCode.LOCATION_PERMISSION_DENIED -> AppErrorType.PERMISSION_DENIED
            TerminalException.TerminalErrorCode.READER_DISCONNECTED -> AppErrorType.READER_DISCONNECTED
            TerminalException.TerminalErrorCode.REQUEST_TIMED_OUT -> AppErrorType.TIMEOUT
            else -> AppErrorType.UNKNOWN
        }
        return AppError(type, errorCode?.name ?: "TERMINAL_ERROR", errorMessage ?: message ?: "Reader error")
    }
}
