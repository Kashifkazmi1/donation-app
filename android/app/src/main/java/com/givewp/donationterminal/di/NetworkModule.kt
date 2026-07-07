package com.givewp.donationterminal.di

import com.givewp.donationterminal.BuildConfig
import com.givewp.donationterminal.data.local.prefs.SettingsDataStore
import com.givewp.donationterminal.data.remote.api.AuthApi
import com.givewp.donationterminal.data.remote.api.PaymentsApi
import com.givewp.donationterminal.data.remote.api.TerminalApi
import com.givewp.donationterminal.data.remote.api.TransactionsApi
import com.givewp.donationterminal.data.remote.interceptor.AuthInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @DefaultApiBaseUrl
    fun provideDefaultApiBaseUrl(): String = BuildConfig.DEFAULT_API_BASE_URL

    // All DTOs are annotated with @JsonClass(generateAdapter = true) and processed by the
    // moshi-kotlin-codegen KSP plugin at compile time, so no reflective Kotlin adapter factory
    // is needed (and moshi-kotlin, the reflection artifact, isn't even on the classpath).
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor as Interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The base URL is a mutable Settings value (see Settings screen / [SettingsDataStore]), not
     * a compile-time constant. Retrofit is rebuilt from a fresh [SettingsDataStore] read at
     * injection time; the app process is relaunched after a base-URL change is saved (see
     * SettingsViewModel) so every Retrofit consumer gets a consistent, up-to-date client.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        settingsDataStore: SettingsDataStore,
        @DefaultApiBaseUrl defaultApiBaseUrl: String
    ): Retrofit {
        val baseUrl = runBlocking { settingsDataStore.current().apiBaseUrl }
            .let { if (it.endsWith("/")) it else "$it/" }
            .let { candidate -> if (candidate.toHttpUrlOrNullSafe() != null) candidate else defaultApiBaseUrl }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private fun String.toHttpUrlOrNullSafe(): okhttp3.HttpUrl? = try {
        this.toHttpUrl()
    } catch (e: IllegalArgumentException) {
        null
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTerminalApi(retrofit: Retrofit): TerminalApi = retrofit.create(TerminalApi::class.java)

    @Provides
    @Singleton
    fun providePaymentsApi(retrofit: Retrofit): PaymentsApi = retrofit.create(PaymentsApi::class.java)

    @Provides
    @Singleton
    fun provideTransactionsApi(retrofit: Retrofit): TransactionsApi = retrofit.create(TransactionsApi::class.java)
}
