package com.givewp.donationterminal.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.data.connectivity.NetworkMonitor
import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import com.givewp.donationterminal.domain.repository.PaymentRepository
import com.givewp.donationterminal.domain.repository.ReaderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the full payment state machine:
 * creating PaymentIntent -> waiting for card (Terminal SDK collect) -> processing/confirming ->
 * confirming with backend (/donations/complete). Every step is safe to retry with the same
 * [com.givewp.donationterminal.domain.model.DonationDraft.idempotencyKey].
 */
@HiltViewModel
class PaymentProcessingViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val readerRepository: ReaderRepository,
    private val donationSessionRepository: DonationSessionRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentProcessingUiState>(PaymentProcessingUiState.CreatingIntent)
    val uiState: StateFlow<PaymentProcessingUiState> = _uiState.asStateFlow()

    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        run()
    }

    fun retry() {
        if (isRunning) return
        isRunning = true
        run()
    }

    fun cancel() {
        readerRepository.cancelCollectPaymentMethod()
        val error = AppError(AppErrorType.STRIPE_CANCELLED, "CANCELLED_BY_STAFF", "Payment was cancelled.")
        donationSessionRepository.setFailure(error)
        _uiState.value = PaymentProcessingUiState.Failed(error)
        isRunning = false
    }

    private fun run() {
        viewModelScope.launch {
            val draft = donationSessionRepository.draft.value
            if (draft == null) {
                fail(AppError(AppErrorType.UNKNOWN, "NO_DRAFT", "No donation in progress."))
                return@launch
            }

            if (!networkMonitor.isCurrentlyOnline()) {
                fail(AppError(AppErrorType.NETWORK, "NO_CONNECTION", "No internet connection. Check your connection and retry."))
                return@launch
            }

            // Step 1: create (or reuse) the PaymentIntent. Reusing an already-created intent for
            // this draft on retry avoids creating a second Stripe PaymentIntent for one donation.
            _uiState.value = PaymentProcessingUiState.CreatingIntent
            val existingIntent = donationSessionRepository.paymentIntent.value
            val intent = existingIntent ?: when (val result = paymentRepository.createPaymentIntent(draft)) {
                is AppResult.Success -> result.data.also { donationSessionRepository.setPaymentIntent(it) }
                is AppResult.Failure -> {
                    fail(result.error)
                    return@launch
                }
            }

            // Step 2 & 3: collect card + confirm with Stripe via the Terminal SDK.
            _uiState.value = PaymentProcessingUiState.WaitingForCard
            val collection = readerRepository.collectAndConfirmPayment(intent.clientSecret)
            if (collection is AppResult.Failure) {
                fail(collection.error)
                return@launch
            }
            _uiState.value = PaymentProcessingUiState.ProcessingPayment
            val collectionResult = (collection as AppResult.Success).data
            donationSessionRepository.setCollectionResult(collectionResult)

            // Step 4: tell the backend the PaymentIntent succeeded so it can create the GiveWP
            // donation. Idempotent -- safe to retry with the same idempotencyKey.
            _uiState.value = PaymentProcessingUiState.ConfirmingWithBackend
            when (val completeResult = paymentRepository.completeDonation(
                collectionResult.paymentIntentId,
                draft.idempotencyKey
            )) {
                is AppResult.Success -> {
                    donationSessionRepository.setCompletedTransaction(completeResult.data)
                    _uiState.value = PaymentProcessingUiState.Success
                }
                is AppResult.Failure -> fail(completeResult.error)
            }
            isRunning = false
        }
    }

    private fun fail(error: AppError) {
        donationSessionRepository.setFailure(error)
        _uiState.value = PaymentProcessingUiState.Failed(error)
        isRunning = false
    }
}
