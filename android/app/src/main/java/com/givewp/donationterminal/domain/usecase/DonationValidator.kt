package com.givewp.donationterminal.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

/** Result of validating the raw amount text entered on the New Donation screen. */
data class AmountValidationResult(
    val isValid: Boolean,
    val amountCents: Long? = null,
    val errorMessage: String? = null
)

/**
 * Pure, side-effect-free validation logic shared by the New Donation ViewModel and unit tests.
 * Kept out of any Android framework classes so it can run under plain JUnit.
 */
object DonationValidator {

    /** Smallest currency unit amounts accepted by the backend must be a positive integer. */
    private val MAX_AMOUNT_MAJOR_UNITS = BigDecimal("999999.99")
    private val AMOUNT_PATTERN = Pattern.compile("^\\d{1,9}(\\.\\d{1,2})?$")

    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    /**
     * [rawInput] is the major-unit text the user typed (e.g. "25", "25.5", "25.00"). Returns the
     * integer smallest-currency-unit ("cents") amount the API expects on success.
     */
    fun validateAmount(rawInput: String): AmountValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return AmountValidationResult(isValid = false, errorMessage = "Enter a donation amount")
        }
        if (!AMOUNT_PATTERN.matcher(trimmed).matches()) {
            return AmountValidationResult(
                isValid = false,
                errorMessage = "Enter a valid amount (up to 2 decimal places)"
            )
        }
        val decimal = try {
            BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            return AmountValidationResult(isValid = false, errorMessage = "Enter a valid amount")
        }
        if (decimal <= BigDecimal.ZERO) {
            return AmountValidationResult(isValid = false, errorMessage = "Amount must be greater than zero")
        }
        if (decimal > MAX_AMOUNT_MAJOR_UNITS) {
            return AmountValidationResult(isValid = false, errorMessage = "Amount is too large")
        }
        val cents = decimal.setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
        if (cents <= 0L) {
            return AmountValidationResult(isValid = false, errorMessage = "Amount must be greater than zero")
        }
        return AmountValidationResult(isValid = true, amountCents = cents)
    }

    /** Email is optional; blank/null is valid. When present, it must look like an email. */
    fun isEmailValid(email: String?): Boolean {
        if (email.isNullOrBlank()) return true
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    fun formatCentsAsMajorUnits(cents: Long): String {
        return BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}
