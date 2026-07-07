package com.givewp.donationterminal.di

import com.givewp.donationterminal.data.repository.AuthRepositoryImpl
import com.givewp.donationterminal.data.repository.DonationSessionRepositoryImpl
import com.givewp.donationterminal.data.repository.PaymentRepositoryImpl
import com.givewp.donationterminal.data.repository.SettingsRepositoryImpl
import com.givewp.donationterminal.data.repository.TransactionRepositoryImpl
import com.givewp.donationterminal.data.terminal.TerminalManager
import com.givewp.donationterminal.domain.repository.AuthRepository
import com.givewp.donationterminal.domain.repository.DonationSessionRepository
import com.givewp.donationterminal.domain.repository.PaymentRepository
import com.givewp.donationterminal.domain.repository.ReaderRepository
import com.givewp.donationterminal.domain.repository.SettingsRepository
import com.givewp.donationterminal.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDonationSessionRepository(impl: DonationSessionRepositoryImpl): DonationSessionRepository

    @Binds
    @Singleton
    abstract fun bindReaderRepository(impl: TerminalManager): ReaderRepository
}
