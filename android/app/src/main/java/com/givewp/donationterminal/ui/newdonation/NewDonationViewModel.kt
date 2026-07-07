package com.givewp.donationterminal.ui.newdonation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.DonorInfo
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import com.givewp.donationterminal.domain.repository.SettingsRepository
import com.givewp.donationterminal.domain.usecase.DonationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewDonationUiState(
    val amountText: String = "",
    val amountError: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val emailError: String? = null,
    val phone: String = "",
    val anonymous: Boolean = false,
    val currency: String = "USD",
    val readyToContinue: Boolean = false,
    val navigateToReaderConnection: Boolean = false
)

@HiltViewModel
class NewDonationViewModel @Inject constructor(
    private val donationSessionRepository: DonationSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewDonationUiState())
    val uiState: StateFlow<NewDonationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            _uiState.update { it.copy(currency = settings.currency) }
        }
    }

    fun onAmountChanged(value: String) {
        // Restrict to digits and a single decimal point as the user types.
        val filtered = value.filterIndexed { index, c ->
            c.isDigit() || (c == '.' && value.indexOf('.') == index)
        }
        _uiState.update { it.copy(amountText = filtered, amountError = null) }
        revalidate()
    }

    fun onFirstNameChanged(value: String) = _uiState.update { it.copy(firstName = value) }
    fun onLastNameChanged(value: String) = _uiState.update { it.copy(lastName = value) }
    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, emailError = null) }
        revalidate()
    }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onAnonymousToggled(value: Boolean) = _uiState.update { it.copy(anonymous = value) }

    private fun revalidate() {
        val state = _uiState.value
        val amountValid = DonationValidator.validateAmount(state.amountText).isValid
        val emailValid = DonationValidator.isEmailValid(state.email)
        _uiState.update { it.copy(readyToContinue = amountValid && emailValid) }
    }

    fun onContinueClicked() {
        val state = _uiState.value
        val amountResult = DonationValidator.validateAmount(state.amountText)
        val emailValid = DonationValidator.isEmailValid(state.email)

        if (!amountResult.isValid || !emailValid) {
            _uiState.update {
                it.copy(
                    amountError = amountResult.errorMessage,
                    emailError = if (!emailValid) "Enter a valid email address" else null
                )
            }
            return
        }

        val draft = DonationDraft(
            amountCents = amountResult.amountCents!!,
            currency = state.currency,
            anonymous = state.anonymous,
            donor = DonorInfo(
                firstName = state.firstName.trim().ifBlank { null },
                lastName = state.lastName.trim().ifBlank { null },
                email = state.email.trim().ifBlank { null },
                phone = state.phone.trim().ifBlank { null }
            )
        )
        donationSessionRepository.startNewDonation(draft)
        _uiState.update { it.copy(navigateToReaderConnection = true) }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(navigateToReaderConnection = false) }
    }
}
