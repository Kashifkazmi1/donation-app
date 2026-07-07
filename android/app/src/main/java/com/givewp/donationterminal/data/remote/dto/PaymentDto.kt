package com.givewp.donationterminal.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Donor shape sent on the /payments/intent request -- no `anonymous` field (that's a sibling). */
@JsonClass(generateAdapter = true)
data class DonorDto(
    @Json(name = "firstName") val firstName: String? = null,
    @Json(name = "lastName") val lastName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null
)

/** Donor shape returned inside a transaction (/donations/complete, /transactions) -- includes `anonymous`. */
@JsonClass(generateAdapter = true)
data class TransactionDonorDto(
    @Json(name = "firstName") val firstName: String? = null,
    @Json(name = "lastName") val lastName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "anonymous") val anonymous: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreatePaymentIntentRequestDto(
    @Json(name = "amount") val amount: Long,
    @Json(name = "currency") val currency: String,
    @Json(name = "anonymous") val anonymous: Boolean,
    @Json(name = "donor") val donor: DonorDto?,
    @Json(name = "idempotencyKey") val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class PaymentIntentResponseDto(
    @Json(name = "paymentIntentId") val paymentIntentId: String,
    @Json(name = "clientSecret") val clientSecret: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "currency") val currency: String
)

@JsonClass(generateAdapter = true)
data class CompleteDonationRequestDto(
    @Json(name = "paymentIntentId") val paymentIntentId: String,
    @Json(name = "idempotencyKey") val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class TransactionDto(
    @Json(name = "transactionId") val transactionId: String,
    @Json(name = "givewpDonationId") val givewpDonationId: Long?,
    @Json(name = "amount") val amount: Long,
    @Json(name = "currency") val currency: String,
    @Json(name = "donor") val donor: TransactionDonorDto,
    @Json(name = "stripePaymentIntentId") val stripePaymentIntentId: String,
    @Json(name = "stripeChargeId") val stripeChargeId: String?,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class TransactionsPageResponseDto(
    @Json(name = "items") val items: List<TransactionDto>,
    @Json(name = "page") val page: Int,
    @Json(name = "pageSize") val pageSize: Int,
    @Json(name = "total") val total: Int
)
