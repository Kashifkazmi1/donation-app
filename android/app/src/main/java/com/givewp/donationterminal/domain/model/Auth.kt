package com.givewp.donationterminal.domain.model

data class AuthUser(
    val id: String,
    val username: String,
    val name: String,
    val role: String
)

data class Session(
    val token: String,
    val expiresAtEpochMillis: Long,
    val user: AuthUser
) {
    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis >= expiresAtEpochMillis
}
