package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.TransactionRecord
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the in-progress donation "wizard" state shared across New Donation -> Reader Connection
 * -> Payment Processing -> Success/Failed. A plain in-memory singleton (not persisted) is the
 * simplest correct choice here: these screens are always visited in the same process/session,
 * nothing here needs to survive process death (if it did, the flow restarts from Dashboard,
 * which is the safe behavior for a payment flow anyway).
 */
interface DonationSessionRepository {
    val draft: StateFlow<DonationDraft?>
    val paymentIntent: StateFlow<PaymentIntentInfo?>
    val lastFailure: StateFlow<AppError?>
    val lastResult: StateFlow<TransactionRecord?>
    val lastCollection: StateFlow<PaymentCollectionResult?>

    fun startNewDonation(draft: DonationDraft)
    fun setPaymentIntent(intent: PaymentIntentInfo)
    fun setFailure(error: AppError)
    fun setCollectionResult(result: PaymentCollectionResult)
    fun setCompletedTransaction(transaction: TransactionRecord)
    fun clear()
}
