package com.givewp.donationterminal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationValidatorTest {

    @Test
    fun `blank amount is invalid`() {
        val result = DonationValidator.validateAmount("")
        assertFalse(result.isValid)
        assertNull(result.amountCents)
        assertEquals("Enter a donation amount", result.errorMessage)
    }

    @Test
    fun `whitespace-only amount is invalid`() {
        val result = DonationValidator.validateAmount("   ")
        assertFalse(result.isValid)
    }

    @Test
    fun `zero amount is invalid`() {
        val result = DonationValidator.validateAmount("0")
        assertFalse(result.isValid)
        assertEquals("Amount must be greater than zero", result.errorMessage)
    }

    @Test
    fun `zero amount with decimals is invalid`() {
        val result = DonationValidator.validateAmount("0.00")
        assertFalse(result.isValid)
    }

    @Test
    fun `negative amount is rejected by pattern`() {
        val result = DonationValidator.validateAmount("-5")
        assertFalse(result.isValid)
    }

    @Test
    fun `non-numeric input is invalid`() {
        val result = DonationValidator.validateAmount("abc")
        assertFalse(result.isValid)
    }

    @Test
    fun `more than two decimal places is invalid`() {
        val result = DonationValidator.validateAmount("25.123")
        assertFalse(result.isValid)
    }

    @Test
    fun `whole dollar amount converts to cents correctly`() {
        val result = DonationValidator.validateAmount("25")
        assertTrue(result.isValid)
        assertEquals(2500L, result.amountCents)
    }

    @Test
    fun `amount with cents converts correctly`() {
        val result = DonationValidator.validateAmount("25.50")
        assertTrue(result.isValid)
        assertEquals(2550L, result.amountCents)
    }

    @Test
    fun `single decimal digit is treated as tens of cents`() {
        val result = DonationValidator.validateAmount("25.5")
        assertTrue(result.isValid)
        assertEquals(2550L, result.amountCents)
    }

    @Test
    fun `small amount of one cent is valid`() {
        val result = DonationValidator.validateAmount("0.01")
        assertTrue(result.isValid)
        assertEquals(1L, result.amountCents)
    }

    @Test
    fun `amount over the max is invalid`() {
        val result = DonationValidator.validateAmount("1000000.00")
        assertFalse(result.isValid)
        assertEquals("Amount is too large", result.errorMessage)
    }

    @Test
    fun `amount at the max boundary is valid`() {
        val result = DonationValidator.validateAmount("999999.99")
        assertTrue(result.isValid)
        assertEquals(99999999L, result.amountCents)
    }

    @Test
    fun `blank email is valid because it is optional`() {
        assertTrue(DonationValidator.isEmailValid(null))
        assertTrue(DonationValidator.isEmailValid(""))
        assertTrue(DonationValidator.isEmailValid("   "))
    }

    @Test
    fun `well formed email is valid`() {
        assertTrue(DonationValidator.isEmailValid("jane@example.com"))
        assertTrue(DonationValidator.isEmailValid("jane.doe+donations@example.co.uk"))
    }

    @Test
    fun `malformed email is invalid`() {
        assertFalse(DonationValidator.isEmailValid("not-an-email"))
        assertFalse(DonationValidator.isEmailValid("missing@domain"))
        assertFalse(DonationValidator.isEmailValid("@example.com"))
    }

    @Test
    fun `formatCentsAsMajorUnits formats correctly`() {
        assertEquals("25.00", DonationValidator.formatCentsAsMajorUnits(2500L))
        assertEquals("0.01", DonationValidator.formatCentsAsMajorUnits(1L))
        assertEquals("1234.56", DonationValidator.formatCentsAsMajorUnits(123456L))
    }
}
