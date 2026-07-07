package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.data.local.db.TransactionDao
import com.givewp.donationterminal.data.mapper.toDomain
import com.givewp.donationterminal.data.mapper.toEntity
import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.TransactionsApi
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionsApi: TransactionsApi,
    private val transactionDao: TransactionDao,
    private val networkCallExecutor: NetworkCallExecutor
) : TransactionRepository {

    override fun observeCached(): Flow<List<TransactionRecord>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshPage(page: Int, pageSize: Int): AppResult<Boolean> {
        return when (val result = networkCallExecutor.execute { transactionsApi.getTransactions(page, pageSize) }) {
            is AppResult.Success -> {
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    transactionDao.upsertAll(result.data.items.map { it.toEntity(now) })
                }
                val hasMore = page * pageSize < result.data.total
                AppResult.Success(hasMore)
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun getByIdFromCache(transactionId: String): TransactionRecord? =
        withContext(Dispatchers.IO) {
            transactionDao.getById(transactionId)?.toDomain()
        }
}
