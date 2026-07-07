package com.givewp.donationterminal.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.TransactionRepository
import com.givewp.donationterminal.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _transaction = MutableStateFlow<TransactionRecord?>(null)
    val transaction: StateFlow<TransactionRecord?> = _transaction.asStateFlow()

    init {
        val transactionId: String? = savedStateHandle[Screen.TransactionDetail.ARG_TRANSACTION_ID]
        if (transactionId != null) {
            viewModelScope.launch {
                _transaction.value = transactionRepository.getByIdFromCache(transactionId)
            }
        }
    }
}
