package com.givewp.donationterminal.domain.usecase

import java.net.MalformedURLException
import java.net.URL

object SettingsValidator {

    /** Base URL must be a well-formed http(s) URL. Trailing slash is normalized by the caller. */
    fun isValidBaseUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val url = URL(trimmed)
            (url.protocol == "http" || url.protocol == "https") && url.host.isNotBlank()
        } catch (e: MalformedURLException) {
            false
        }
    }

    /** Ensures the base URL Retrofit receives always ends in exactly one trailing slash. */
    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
