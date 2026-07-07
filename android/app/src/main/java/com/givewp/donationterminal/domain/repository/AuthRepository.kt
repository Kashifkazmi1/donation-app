package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the current session (null if logged out / never logged in / expired). */
    val session: Flow<Session?>

    suspend fun login(username: String, password: String): AppResult<Session>

    suspend fun logout(): AppResult<Unit>

    /** Reads the cached session synchronously without hitting the network, for splash routing. */
    suspend fun currentSession(): Session?
}
