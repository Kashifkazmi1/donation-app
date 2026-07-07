package com.givewp.donationterminal.ui.failed

import androidx.lifecycle.ViewModel
import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class RetryDestination { READER_CONNECTION, PAYMENT_PROCESSING }

@HiltViewModel
class PaymentFailedViewModel @Inject constructor(
    private val donationSessionRepository: DonationSessionRepository
) : ViewModel() {

    val error: AppError?
        get() = donationSessionRepository.lastFailure.value

    /**
     * Reader-level problems (disconnect, missing Bluetooth/permissions) send staff back to the
     * Reader Connection screen to reconnect; everything else (decline, cancel, network, timeout)
     * retries the payment flow directly since the reader is presumably still connected.
     */
    fun retryDestination(): RetryDestination {
        return when (error?.type) {
            AppErrorType.READER_DISCONNECTED,
            AppErrorType.BLUETOOTH_DISABLED,
            AppErrorType.PERMISSION_DENIED -> RetryDestination.READER_CONNECTION
            else -> RetryDestination.PAYMENT_PROCESSING
        }
    }

    fun onCancel() {
        donationSessionRepository.clear()
    }
}
