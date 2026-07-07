package com.givewp.donationterminal.ui.payment

import com.givewp.donationterminal.domain.model.AppError

/** State machine for the Payment Processing screen (contract: /payments/intent -> Terminal SDK collect/confirm -> /donations/complete). */
sealed class PaymentProcessingUiState {
    data object CreatingIntent : PaymentProcessingUiState()
    data object WaitingForCard : PaymentProcessingUiState()
    data object ProcessingPayment : PaymentProcessingUiState()
    data object ConfirmingWithBackend : PaymentProcessingUiState()
    data object Success : PaymentProcessingUiState()
    data class Failed(val error: AppError) : PaymentProcessingUiState()
}
