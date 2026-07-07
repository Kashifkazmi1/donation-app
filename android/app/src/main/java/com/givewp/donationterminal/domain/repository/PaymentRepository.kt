package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.TransactionRecord

interface PaymentRepository {
    /** POST /payments/intent. Safe to retry with the same [DonationDraft.idempotencyKey]. */
    suspend fun createPaymentIntent(draft: DonationDraft): AppResult<PaymentIntentInfo>

    /**
     * POST /donations/complete. Safe to retry with the same [idempotencyKey] -- the backend
     * returns the existing transaction unchanged if it already processed this PaymentIntent.
     */
    suspend fun completeDonation(
        paymentIntentId: String,
        idempotencyKey: String
    ): AppResult<TransactionRecord>
}
