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
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.MobileReaderListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.BatteryStatus
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.DisconnectReason
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalErrorCode
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
 * NOTE ON SDK VERSION: written against the stripeterminal-core 5.x API surface -- pinned in
 * gradle/libs.versions.toml as `stripeTerminal`. Key points that differ from older (pre-4.0)
 * versions of this SDK, confirmed against the SDK's public CHANGELOG:
 *  - `Terminal.init` (not the older `Terminal.initTerminal`).
 *  - A single unified `Terminal.connectReader(reader, connectionConfig, callback)` for all
 *    reader types (the older per-type `connectBluetoothReader`/`connectUsbReader` are gone).
 *  - The reader listener is now supplied *inside* `ConnectionConfiguration.BluetoothConnectionConfiguration`
 *    (as a [MobileReaderListener]) instead of as a separate parameter to the connect call.
 *  - `MobileReaderListener` (formerly `ReaderListener`/`BluetoothReaderListener` in older
 *    versions) extends `ReaderReconnectionListener` and owns `onDisconnect` -- `TerminalListener`
 *    no longer has `onUnexpectedReaderDisconnect`.
 *  - `TerminalErrorCode` is a standalone top-level enum, not nested inside `TerminalException`.
 * This file could not be compiled in the sandbox this project was authored in (no Android SDK /
 * Stripe Maven access), so before your first real build, diff these call sites against the
 * current `com.stripe:stripeterminal-core` version's sample app / API reference -- treat this as
 * reviewed-against-documentation but unverified by a real compiler.
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

    private val _unexpectedDisconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val unexpectedDisconnect: Flow<Unit> = _unexpectedDisconnect.asSharedFlow()

    private val terminalListener = object : TerminalListener {
        override fun onConnectionStatusChange(status: ConnectionStatus) {
            _connectionStatus.value = when (status) {
                ConnectionStatus.CONNECTED -> ReaderConnectionStatus.CONNECTED
                ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> ReaderConnectionStatus.CONNECTING
                else -> ReaderConnectionStatus.NOT_CONNECTED
            }
        }

        override fun onPaymentStatusChange(status: PaymentStatus) {
            // Exposed for future UI (e.g. a live "waiting for card" indicator); the payment
            // ViewModel currently drives its own state machine from the collect/confirm calls.
        }
    }

    // MobileReaderListener extends ReaderReconnectionListener as of SDK v4+: it owns both the
    // battery/firmware callbacks *and* disconnect/reconnect handling that used to live on
    // TerminalListener.onUnexpectedReaderDisconnect (removed in v4).
    private val mobileReaderListener = object : MobileReaderListener {
        override fun onBatteryLevelUpdate(batteryLevel: Float, isCharging: Boolean, status: BatteryStatus) {
            _batteryLevel.value = batteryLevel
        }

        override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
            // Firmware update progress -- not surfaced in this app's UI, no user action needed.
        }

        override fun onDisconnect(reason: DisconnectReason) {
            _connectionStatus.value = ReaderConnectionStatus.NOT_CONNECTED
            _batteryLevel.value = null
            _unexpectedDisconnect.tryEmit(Unit)
        }

        override fun onReaderReconnectStarted(reader: Reader, cancelReconnect: Cancelable, reason: DisconnectReason) {
            _connectionStatus.value = ReaderConnectionStatus.CONNECTING
        }

        override fun onReaderReconnectSucceeded(reader: Reader) {
            _connectionStatus.value = ReaderConnectionStatus.CONNECTED
            _batteryLevel.value = reader.batteryLevel
        }

        override fun onReaderReconnectFailed(reader: Reader) {
            _connectionStatus.value = ReaderConnectionStatus.NOT_CONNECTED
            _batteryLevel.value = null
            _unexpectedDisconnect.tryEmit(Unit)
        }
    }

    private fun ensureInitialized() {
        if (Terminal.isInitialized()) return
        Terminal.init(
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
        // Stripe's Terminal SDK requires location permission for Bluetooth reader discovery on
        // every Android version -- even on API 31+ where BLUETOOTH_SCAN can otherwise be
        // declared `neverForLocation`, so it's always requested alongside the Bluetooth runtime
        // permissions here, not only pre-S.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
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
            // Written as an explicit anonymous object (rather than a SAM lambda) since it's not
            // guaranteed the SDK declares this as a Kotlin `fun interface` across versions.
            val discoveryListener = object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    lastDiscoveredSdkReaders = readers
                    _discoveredReaders.value = readers.map { it.toDomain() }
                }
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

        // `autoReconnectOnUnexpectedDisconnect = false` here: this app surfaces disconnects to the
        // user explicitly (see [unexpectedDisconnect]) and lets the Reader Connection screen own
        // the retry flow, rather than having the SDK reconnect silently in the background. Note
        // that SDK v4+ defaults this to `true`; we explicitly opt back out for that reason.
        // The mobile reader listener is now part of the connection config (see class doc).
        val connectionConfig = ConnectionConfiguration.BluetoothConnectionConfiguration(
            locationId = locationId,
            autoReconnectOnUnexpectedDisconnect = false,
            mobileReaderListener = mobileReaderListener
        )

        return suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().connectReader(
                sdkReader,
                connectionConfig,
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

        val charge = finalIntent.charges.firstOrNull()
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
        // TerminalErrorCode is a standalone top-level enum as of SDK v4+ (previously nested
        // inside TerminalException).
        val type = when (errorCode) {
            TerminalErrorCode.CANCELED -> AppErrorType.STRIPE_CANCELLED
            TerminalErrorCode.DECLINED_BY_READER,
            TerminalErrorCode.DECLINED_BY_STRIPE_API -> AppErrorType.STRIPE_DECLINED
            TerminalErrorCode.BLUETOOTH_DISABLED,
            TerminalErrorCode.BLUETOOTH_SCAN_TIMED_OUT -> AppErrorType.BLUETOOTH_DISABLED
            TerminalErrorCode.BLUETOOTH_PERMISSION_DENIED,
            TerminalErrorCode.LOCATION_PERMISSION_DENIED -> AppErrorType.PERMISSION_DENIED
            TerminalErrorCode.READER_DISCONNECTED -> AppErrorType.READER_DISCONNECTED
            TerminalErrorCode.REQUEST_TIMED_OUT -> AppErrorType.TIMEOUT
            else -> AppErrorType.UNKNOWN
        }
        return AppError(type, errorCode?.name ?: "TERMINAL_ERROR", errorMessage ?: message ?: "Reader error")
    }
}
