package com.givewp.donationterminal.domain.model

/**
 * Result type returned by every repository method at the domain boundary. Keeps ViewModels free
 * of Retrofit/OkHttp/Room types so they stay unit-testable with plain fakes.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

/** Category used to decide what recovery UI to show (retry button, banner, decline reason, etc). */
enum class AppErrorType {
    NETWORK,
    TIMEOUT,
    VALIDATION,
    AUTH,
    SERVER,
    STRIPE_DECLINED,
    STRIPE_CANCELLED,
    READER_DISCONNECTED,
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    UNKNOWN
}

data class AppError(
    val type: AppErrorType,
    val code: String,
    val message: String
)

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}
