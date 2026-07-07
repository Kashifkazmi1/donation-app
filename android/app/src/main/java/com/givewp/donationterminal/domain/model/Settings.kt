package com.givewp.donationterminal.domain.model

data class AppSettings(
    val apiBaseUrl: String,
    val terminalLocationId: String,
    val currency: String,
    val testMode: Boolean
) {
    companion object {
        val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "GBP", "CAD", "AUD", "NZD", "JPY")

        fun default(defaultBaseUrl: String) = AppSettings(
            apiBaseUrl = defaultBaseUrl,
            terminalLocationId = "",
            currency = "USD",
            testMode = true
        )
    }
}
