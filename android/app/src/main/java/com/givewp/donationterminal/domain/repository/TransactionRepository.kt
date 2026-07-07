package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.TransactionRecord
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    /** Reactive view of the Room cache, most recent first. Works fully offline. */
    fun observeCached(): Flow<List<TransactionRecord>>

    /**
     * Fetches [page] from the backend, upserts it into the Room cache, and returns whether more
     * pages remain. On network failure, returns [AppResult.Failure] but the cache (and
     * [observeCached]) is untouched, so the UI can keep showing what it already has.
     */
    suspend fun refreshPage(page: Int, pageSize: Int = 20): AppResult<Boolean>

    suspend fun getByIdFromCache(transactionId: String): TransactionRecord?
}
