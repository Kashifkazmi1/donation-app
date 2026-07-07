package com.givewp.donationterminal.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

data class TransactionHistoryUiState(
    val transactions: List<TransactionRecord> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null,
    val currentPage: Int = 1
)

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _canLoadMore = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _currentPage = MutableStateFlow(1)

    val uiState: StateFlow<TransactionHistoryUiState> = combine(
        transactionRepository.observeCached(),
        _isRefreshing,
        _isLoadingMore,
        _canLoadMore,
        _errorMessage
    ) { transactions, refreshing, loadingMore, canLoadMore, error ->
        TransactionHistoryUiState(
            transactions = transactions,
            isRefreshing = refreshing,
            isLoadingMore = loadingMore,
            canLoadMore = canLoadMore,
            errorMessage = error,
            currentPage = _currentPage.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionHistoryUiState())

    init {
        refresh()
    }

    fun refresh() {
        _isRefreshing.value = true
        _errorMessage.value = null
        _currentPage.value = 1
        viewModelScope.launch {
            when (val result = transactionRepository.refreshPage(page = 1, pageSize = PAGE_SIZE)) {
                is AppResult.Success -> _canLoadMore.value = result.data
                is AppResult.Failure -> _errorMessage.value = result.error.message
            }
            _isRefreshing.value = false
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_canLoadMore.value || _isRefreshing.value) return
        val nextPage = _currentPage.value + 1
        _isLoadingMore.value = true
        viewModelScope.launch {
            when (val result = transactionRepository.refreshPage(page = nextPage, pageSize = PAGE_SIZE)) {
                is AppResult.Success -> {
                    _currentPage.value = nextPage
                    _canLoadMore.value = result.data
                }
                is AppResult.Failure -> _errorMessage.value = result.error.message
            }
            _isLoadingMore.value = false
        }
    }
}
