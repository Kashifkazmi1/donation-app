package com.givewp.donationterminal.domain.repository

import com.givewp.donationterminal.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun currentSettings(): AppSettings

    suspend fun save(settings: AppSettings)
}
