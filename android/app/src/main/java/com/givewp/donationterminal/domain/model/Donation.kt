package com.givewp.donationterminal.domain.model

import java.util.UUID

/**
 * Donor-supplied information for a donation. Every field is optional per the API contract.
 */
data class DonorInfo(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null
) {
    val fullName: String?
        get() = listOfNotNull(firstName?.trim()?.ifBlank { null }, lastName?.trim()?.ifBlank { null })
            .joinToString(" ")
            .ifBlank { null }

    val isEmpty: Boolean
        get() = firstName.isNullOrBlank() && lastName.isNullOrBlank() &&
            email.isNullOrBlank() && phone.isNullOrBlank()
}

/**
 * In-memory draft of a donation being created on the New Donation screen, carried through the
 * Reader Connection -> Payment Processing flow. [idempotencyKey] is generated exactly once per
 * donation attempt and reused for every retry of /payments/intent and /donations/complete so
 * that retries after a network blip never create duplicate PaymentIntents or GiveWP donations.
 */
data class DonationDraft(
    val amountCents: Long,
    val currency: String,
    val anonymous: Boolean,
    val donor: DonorInfo,
    val idempotencyKey: String = UUID.randomUUID().toString()
) {
    companion object {
        fun empty(currency: String) = DonationDraft(
            amountCents = 0L,
            currency = currency,
            anonymous = false,
            donor = DonorInfo()
        )
    }
}

/** Result of POST /payments/intent - a Stripe PaymentIntent ready for Terminal collection. */
data class PaymentIntentInfo(
    val paymentIntentId: String,
    val clientSecret: String,
    val amount: Long,
    val currency: String
)

/**
 * Result of the on-device Terminal SDK collect+confirm flow. [cardBrand]/[cardLast4] come from
 * the confirmed PaymentIntent's charge, when the SDK exposes them, for the receipt screen.
 */
data class PaymentCollectionResult(
    val paymentIntentId: String,
    val cardBrand: String?,
    val cardLast4: String?
)

/** A completed (or historical) donation/transaction, mirroring the backend's transaction shape. */
data class TransactionRecord(
    val transactionId: String,
    val givewpDonationId: Long?,
    val amount: Long,
    val currency: String,
    val donor: DonorInfo,
    val anonymous: Boolean,
    val stripePaymentIntentId: String,
    val stripeChargeId: String?,
    val status: String,
    val createdAt: String
)
