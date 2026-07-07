package com.givewp.donationterminal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.repository.AuthRepository
import com.givewp.donationterminal.domain.repository.ReaderConnectionStatus
import com.givewp.donationterminal.domain.repository.ReaderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    readerRepository: ReaderRepository
) : ViewModel() {

    val readerStatus: StateFlow<ReaderConnectionStatus> = readerRepository.connectionStatus.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderConnectionStatus.NOT_CONNECTED
    )

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    val userName: StateFlow<String?> = authRepository.session
        .map { it?.user?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = true
        }
    }
}
