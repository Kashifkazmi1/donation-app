package com.givewp.donationterminal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.AppSettings
import com.givewp.donationterminal.domain.repository.SettingsRepository
import com.givewp.donationterminal.domain.usecase.SettingsValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiBaseUrl: String = "",
    val apiBaseUrlError: String? = null,
    val terminalLocationId: String = "",
    val currency: String = "USD",
    val testMode: Boolean = true,
    val isLoading: Boolean = true,
    val savedSuccessfully: Boolean = false,
    val baseUrlChanged: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var originalBaseUrl: String = ""

    init {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            originalBaseUrl = settings.apiBaseUrl
            _uiState.update {
                it.copy(
                    apiBaseUrl = settings.apiBaseUrl,
                    terminalLocationId = settings.terminalLocationId,
                    currency = settings.currency,
                    testMode = settings.testMode,
                    isLoading = false
                )
            }
        }
    }

    fun onApiBaseUrlChanged(value: String) =
        _uiState.update { it.copy(apiBaseUrl = value, apiBaseUrlError = null, savedSuccessfully = false) }

    fun onTerminalLocationIdChanged(value: String) =
        _uiState.update { it.copy(terminalLocationId = value, savedSuccessfully = false) }

    fun onCurrencyChanged(value: String) =
        _uiState.update { it.copy(currency = value, savedSuccessfully = false) }

    fun onTestModeChanged(value: Boolean) =
        _uiState.update { it.copy(testMode = value, savedSuccessfully = false) }

    fun onSaveClicked() {
        val state = _uiState.value
        if (!SettingsValidator.isValidBaseUrl(state.apiBaseUrl)) {
            _uiState.update { it.copy(apiBaseUrlError = "Enter a valid http(s) URL") }
            return
        }
        val normalizedUrl = SettingsValidator.normalizeBaseUrl(state.apiBaseUrl)
        viewModelScope.launch {
            settingsRepository.save(
                AppSettings(
                    apiBaseUrl = normalizedUrl,
                    terminalLocationId = state.terminalLocationId.trim(),
                    currency = state.currency,
                    testMode = state.testMode
                )
            )
            _uiState.update {
                it.copy(
                    apiBaseUrl = normalizedUrl,
                    savedSuccessfully = true,
                    baseUrlChanged = normalizedUrl != originalBaseUrl
                )
            }
        }
    }
}
