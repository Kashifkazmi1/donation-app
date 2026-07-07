package com.givewp.donationterminal.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.givewp.donationterminal.domain.model.AuthUser
import com.givewp.donationterminal.domain.model.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the JWT + user profile in EncryptedSharedPreferences (AES-256-GCM backed by the
 * Android Keystore). Never store the token in plain DataStore/SharedPreferences.
 */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getSession(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        return Session(
            token = token,
            expiresAtEpochMillis = expiresAt,
            user = AuthUser(id = userId, username = username, name = name, role = role)
        )
    }

    fun saveSession(session: Session) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochMillis)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USERNAME, session.user.username)
            .putString(KEY_NAME, session.user.name)
            .putString(KEY_ROLE, session.user.role)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "donation_terminal_secure_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EXPIRES_AT = "jwt_expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_username"
        private const val KEY_NAME = "user_name"
        private const val KEY_ROLE = "user_role"
    }
}
