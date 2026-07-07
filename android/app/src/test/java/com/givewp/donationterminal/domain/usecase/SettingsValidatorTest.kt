package com.givewp.donationterminal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {

    @Test
    fun `https url is valid`() {
        assertTrue(SettingsValidator.isValidBaseUrl("https://api.example.org/api/v1/"))
    }

    @Test
    fun `http url is valid`() {
        assertTrue(SettingsValidator.isValidBaseUrl("http://10.0.2.2:3000/api/v1/"))
    }

    @Test
    fun `blank url is invalid`() {
        assertFalse(SettingsValidator.isValidBaseUrl(""))
        assertFalse(SettingsValidator.isValidBaseUrl("   "))
    }

    @Test
    fun `url without scheme is invalid`() {
        assertFalse(SettingsValidator.isValidBaseUrl("api.example.org/api/v1/"))
    }

    @Test
    fun `non http scheme is invalid`() {
        assertFalse(SettingsValidator.isValidBaseUrl("ftp://api.example.org/"))
    }

    @Test
    fun `normalizeBaseUrl adds trailing slash`() {
        assertEquals("https://api.example.org/api/v1/", SettingsValidator.normalizeBaseUrl("https://api.example.org/api/v1"))
    }

    @Test
    fun `normalizeBaseUrl leaves existing trailing slash alone`() {
        assertEquals("https://api.example.org/api/v1/", SettingsValidator.normalizeBaseUrl("https://api.example.org/api/v1/"))
    }
}
