package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.data.local.prefs.TokenStorage
import com.givewp.donationterminal.data.mapper.toDomainSession
import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.AuthApi
import com.givewp.donationterminal.data.remote.dto.LoginRequestDto
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.Session
import com.givewp.donationterminal.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val networkCallExecutor: NetworkCallExecutor
) : AuthRepository {

    private val _session = MutableStateFlow(tokenStorage.getSession())
    override val session = _session.asStateFlow()

    override suspend fun login(username: String, password: String): AppResult<Session> {
        val result = networkCallExecutor.execute {
            authApi.login(LoginRequestDto(username = username, password = password))
        }
        return when (result) {
            is AppResult.Success -> {
                val session = result.data.toDomainSession(System.currentTimeMillis())
                tokenStorage.saveSession(session)
                _session.value = session
                AppResult.Success(session)
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun logout(): AppResult<Unit> {
        // Best-effort server-side blacklist; clear local session regardless of network outcome so
        // a signed-out device is never stuck unable to log out while offline.
        val result = networkCallExecutor.execute { authApi.logout() }
        tokenStorage.clear()
        _session.value = null
        return when (result) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Success(Unit)
        }
    }

    override suspend fun currentSession(): Session? = tokenStorage.getSession()
}
