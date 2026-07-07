package com.givewp.donationterminal.ui.success

import androidx.lifecycle.ViewModel
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import com.givewp.donationterminal.domain.usecase.DonationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class PaymentSuccessUiState(
    val transaction: TransactionRecord?,
    val collectionResult: PaymentCollectionResult?,
    val draft: DonationDraft?
) {
    val formattedAmount: String
        get() = transaction?.let { "${it.currency.uppercase()} ${DonationValidator.formatCentsAsMajorUnits(it.amount)}" }
            ?: ""
}

@HiltViewModel
class PaymentSuccessViewModel @Inject constructor(
    private val donationSessionRepository: DonationSessionRepository
) : ViewModel() {

    val uiState: PaymentSuccessUiState
        get() = PaymentSuccessUiState(
            transaction = donationSessionRepository.lastResult.value,
            collectionResult = donationSessionRepository.lastCollection.value,
            draft = donationSessionRepository.draft.value
        )

    fun onDoneWithFlow() {
        donationSessionRepository.clear()
    }
}
