package com.givewp.donationterminal.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DiscoveredReader
import com.givewp.donationterminal.domain.model.ReaderConnectionPhase
import com.givewp.donationterminal.domain.model.ReaderConnectionUiState
import com.givewp.donationterminal.domain.repository.ReaderRepository
import com.givewp.donationterminal.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Time to let the Bluetooth scan settle before deciding whether to auto-connect. */
private const val DISCOVERY_SETTLE_DELAY_MS = 2500L
private const val DISCOVERY_TIMEOUT_MS = 15000L

@HiltViewModel
class ReaderConnectionViewModel @Inject constructor(
    private val readerRepository: ReaderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderConnectionUiState())
    val uiState: StateFlow<ReaderConnectionUiState> = _uiState.asStateFlow()

    private val _navigateToPayment = MutableStateFlow(false)
    val navigateToPayment: StateFlow<Boolean> = _navigateToPayment.asStateFlow()

    private var settleJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        viewModelScope.launch {
            readerRepository.batteryLevel.collect { level ->
                _uiState.update { it.copy(batteryLevel = level) }
            }
        }
        viewModelScope.launch {
            readerRepository.unexpectedDisconnect.collect {
                _uiState.update {
                    it.copy(
                        phase = ReaderConnectionPhase.ERROR,
                        connectedReader = null,
                        errorMessage = "The reader disconnected unexpectedly. Please reconnect."
                    )
                }
            }
        }
        viewModelScope.launch {
            readerRepository.discoveredReaders.collect { readers ->
                onDiscoveredReadersChanged(readers)
            }
        }
    }

    fun requiredPermissions(): Array<String> = readerRepository.requiredPermissions()

    /** Called from the Composable on entry and whenever it resumes (e.g. back from Settings). */
    fun checkRequirementsAndStart() {
        if (!readerRepository.hasRequiredPermissions()) {
            _uiState.update { it.copy(phase = ReaderConnectionPhase.MISSING_PERMISSIONS) }
            return
        }
        if (!readerRepository.isBluetoothEnabled()) {
            _uiState.update { it.copy(phase = ReaderConnectionPhase.BLUETOOTH_DISABLED) }
            return
        }
        startDiscovery()
    }

    fun onPermissionsResult(allGranted: Boolean) {
        if (allGranted) {
            checkRequirementsAndStart()
        } else {
            _uiState.update {
                it.copy(
                    phase = ReaderConnectionPhase.MISSING_PERMISSIONS,
                    errorMessage = "Bluetooth permission is required to discover the card reader."
                )
            }
        }
    }

    fun onBluetoothEnableResult(enabled: Boolean) {
        if (enabled) checkRequirementsAndStart() else {
            _uiState.update { it.copy(phase = ReaderConnectionPhase.BLUETOOTH_DISABLED) }
        }
    }

    private fun startDiscovery() {
        settleJob?.cancel()
        timeoutJob?.cancel()
        _uiState.update {
            it.copy(phase = ReaderConnectionPhase.DISCOVERING, discoveredReaders = emptyList(), errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = readerRepository.startDiscovery()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(phase = ReaderConnectionPhase.ERROR, errorMessage = result.error.message)
                }
                is AppResult.Success -> {
                    timeoutJob = viewModelScope.launch {
                        delay(DISCOVERY_TIMEOUT_MS)
                        if (_uiState.value.phase == ReaderConnectionPhase.DISCOVERING) {
                            _uiState.update { it.copy(phase = ReaderConnectionPhase.NO_READERS_FOUND) }
                        }
                    }
                }
            }
        }
    }

    private fun onDiscoveredReadersChanged(readers: List<DiscoveredReader>) {
        if (_uiState.value.phase != ReaderConnectionPhase.DISCOVERING &&
            _uiState.value.phase != ReaderConnectionPhase.AWAITING_SELECTION
        ) {
            return
        }
        _uiState.update { it.copy(discoveredReaders = readers) }
        settleJob?.cancel()
        if (readers.isEmpty()) return

        settleJob = viewModelScope.launch {
            delay(DISCOVERY_SETTLE_DELAY_MS)
            val current = _uiState.value.discoveredReaders
            if (current.size == 1) {
                connectToReader(current.first())
            } else if (current.size > 1) {
                _uiState.update { it.copy(phase = ReaderConnectionPhase.AWAITING_SELECTION) }
            }
        }
    }

    fun onReaderSelected(reader: DiscoveredReader) {
        connectToReader(reader)
    }

    private fun connectToReader(reader: DiscoveredReader) {
        timeoutJob?.cancel()
        settleJob?.cancel()
        _uiState.update { it.copy(phase = ReaderConnectionPhase.CONNECTING) }
        viewModelScope.launch {
            val locationId = settingsRepository.currentSettings().terminalLocationId
            if (locationId.isBlank()) {
                _uiState.update {
                    it.copy(
                        phase = ReaderConnectionPhase.ERROR,
                        errorMessage = "Set a Terminal Location ID in Settings before connecting a reader."
                    )
                }
                return@launch
            }
            when (val result = readerRepository.connect(reader, locationId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        phase = ReaderConnectionPhase.CONNECTED,
                        connectedReader = result.data,
                        errorMessage = null
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(phase = ReaderConnectionPhase.ERROR, errorMessage = result.error.message)
                }
            }
        }
    }

    fun retry() {
        checkRequirementsAndStart()
    }

    fun onCollectPaymentClicked() {
        _navigateToPayment.value = true
    }

    fun consumeNavigationEvent() {
        _navigateToPayment.value = false
    }

    override fun onCleared() {
        super.onCleared()
        readerRepository.stopDiscovery()
    }
}
