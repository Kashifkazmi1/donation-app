package com.givewp.donationterminal.data.terminal

import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.TerminalApi
import com.givewp.donationterminal.di.ApplicationScope
import com.givewp.donationterminal.domain.model.AppResult
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the Terminal SDK's callback-based [ConnectionTokenProvider] to the backend's
 * POST /terminal/connection-token endpoint. The app never talks to Stripe directly for this --
 * only the backend (holding the Stripe secret key) can mint a connection token.
 */
@Singleton
class ConnectionTokenProviderImpl @Inject constructor(
    private val terminalApi: TerminalApi,
    private val networkCallExecutor: NetworkCallExecutor,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ConnectionTokenProvider {

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        applicationScope.launch {
            val result = networkCallExecutor.execute { terminalApi.createConnectionToken() }
            when (result) {
                is AppResult.Success -> callback.onSuccess(result.data.secret)
                is AppResult.Failure -> callback.onFailure(
                    ConnectionTokenException(result.error.message)
                )
            }
        }
    }
}
