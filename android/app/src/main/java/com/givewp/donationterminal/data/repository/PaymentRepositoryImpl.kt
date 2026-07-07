package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.data.local.db.TransactionDao
import com.givewp.donationterminal.data.mapper.toDomain
import com.givewp.donationterminal.data.mapper.toDto
import com.givewp.donationterminal.data.mapper.toEntity
import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.PaymentsApi
import com.givewp.donationterminal.data.remote.dto.CompleteDonationRequestDto
import com.givewp.donationterminal.data.remote.dto.CreatePaymentIntentRequestDto
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.PaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val paymentsApi: PaymentsApi,
    private val networkCallExecutor: NetworkCallExecutor,
    private val transactionDao: TransactionDao
) : PaymentRepository {

    override suspend fun createPaymentIntent(draft: DonationDraft): AppResult<PaymentIntentInfo> {
        val request = CreatePaymentIntentRequestDto(
            amount = draft.amountCents,
            currency = draft.currency.lowercase(),
            anonymous = draft.anonymous,
            donor = draft.donor.toDto(),
            idempotencyKey = draft.idempotencyKey
        )
        return when (val result = networkCallExecutor.execute { paymentsApi.createPaymentIntent(request) }) {
            is AppResult.Success -> AppResult.Success(result.data.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun completeDonation(
        paymentIntentId: String,
        idempotencyKey: String
    ): AppResult<TransactionRecord> {
        val request = CompleteDonationRequestDto(
            paymentIntentId = paymentIntentId,
            idempotencyKey = idempotencyKey
        )
        return when (val result = networkCallExecutor.execute { paymentsApi.completeDonation(request) }) {
            is AppResult.Success -> {
                withContext(Dispatchers.IO) {
                    transactionDao.upsert(result.data.toEntity(System.currentTimeMillis()))
                }
                AppResult.Success(result.data.toDomain())
            }
            is AppResult.Failure -> result
        }
    }
}
