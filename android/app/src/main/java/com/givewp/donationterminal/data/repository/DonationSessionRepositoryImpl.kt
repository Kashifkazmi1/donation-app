package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DonationSessionRepositoryImpl @Inject constructor() : DonationSessionRepository {

    private val _draft = MutableStateFlow<DonationDraft?>(null)
    override val draft: StateFlow<DonationDraft?> = _draft

    private val _paymentIntent = MutableStateFlow<PaymentIntentInfo?>(null)
    override val paymentIntent: StateFlow<PaymentIntentInfo?> = _paymentIntent

    private val _lastFailure = MutableStateFlow<AppError?>(null)
    override val lastFailure: StateFlow<AppError?> = _lastFailure

    private val _lastResult = MutableStateFlow<TransactionRecord?>(null)
    override val lastResult: StateFlow<TransactionRecord?> = _lastResult

    private val _lastCollection = MutableStateFlow<PaymentCollectionResult?>(null)
    override val lastCollection: StateFlow<PaymentCollectionResult?> = _lastCollection

    override fun startNewDonation(draft: DonationDraft) {
        _draft.value = draft
        _paymentIntent.value = null
        _lastFailure.value = null
        _lastResult.value = null
        _lastCollection.value = null
    }

    override fun setPaymentIntent(intent: PaymentIntentInfo) {
        _paymentIntent.value = intent
    }

    override fun setFailure(error: AppError) {
        _lastFailure.value = error
    }

    override fun setCollectionResult(result: PaymentCollectionResult) {
        _lastCollection.value = result
    }

    override fun setCompletedTransaction(transaction: TransactionRecord) {
        _lastResult.value = transaction
    }

    override fun clear() {
        _draft.value = null
        _paymentIntent.value = null
        _lastFailure.value = null
        _lastResult.value = null
        _lastCollection.value = null
    }
}
