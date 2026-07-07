package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.data.local.prefs.SettingsDataStore
import com.givewp.donationterminal.domain.model.AppSettings
import com.givewp.donationterminal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val settings: Flow<AppSettings> = settingsDataStore.settings

    override suspend fun currentSettings(): AppSettings = settingsDataStore.current()

    override suspend fun save(settings: AppSettings) = settingsDataStore.save(settings)
}
