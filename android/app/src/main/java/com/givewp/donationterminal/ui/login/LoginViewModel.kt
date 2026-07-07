package com.givewp.donationterminal.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val apiError: String? = null,
    val loginSucceeded: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, apiError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, apiError = null) }
    }

    fun onLoginClicked() {
        val state = _uiState.value
        val usernameError = if (state.username.isBlank()) "Username is required" else null
        val passwordError = if (state.password.isBlank()) "Password is required" else null
        if (usernameError != null || passwordError != null) {
            _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, apiError = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(state.username.trim(), state.password)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, loginSucceeded = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, apiError = result.error.message)
                }
            }
        }
    }
}
