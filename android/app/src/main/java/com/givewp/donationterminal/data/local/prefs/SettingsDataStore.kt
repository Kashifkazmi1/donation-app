package com.givewp.donationterminal.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.givewp.donationterminal.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "donation_terminal_settings")

/** Non-secret, on-device settings (API base URL, terminal location, currency, test/live mode). */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val defaultApiBaseUrl: String
) {
    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val TERMINAL_LOCATION_ID = stringPreferencesKey("terminal_location_id")
        val CURRENCY = stringPreferencesKey("currency")
        val TEST_MODE = booleanPreferencesKey("test_mode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: defaultApiBaseUrl,
            terminalLocationId = prefs[Keys.TERMINAL_LOCATION_ID] ?: "",
            currency = prefs[Keys.CURRENCY] ?: "USD",
            testMode = prefs[Keys.TEST_MODE] ?: true
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.API_BASE_URL] = settings.apiBaseUrl
            prefs[Keys.TERMINAL_LOCATION_ID] = settings.terminalLocationId
            prefs[Keys.CURRENCY] = settings.currency
            prefs[Keys.TEST_MODE] = settings.testMode
        }
    }
}
