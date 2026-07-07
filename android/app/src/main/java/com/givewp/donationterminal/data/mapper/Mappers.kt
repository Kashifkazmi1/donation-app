package com.givewp.donationterminal.data.mapper

import com.givewp.donationterminal.data.local.db.TransactionEntity
import com.givewp.donationterminal.data.remote.dto.DonorDto
import com.givewp.donationterminal.data.remote.dto.LoginResponseDto
import com.givewp.donationterminal.data.remote.dto.PaymentIntentResponseDto
import com.givewp.donationterminal.data.remote.dto.TransactionDto
import com.givewp.donationterminal.data.remote.dto.UserDto
import com.givewp.donationterminal.domain.model.AuthUser
import com.givewp.donationterminal.domain.model.DonorInfo
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.Session
import com.givewp.donationterminal.domain.model.TransactionRecord

fun UserDto.toDomain(): AuthUser = AuthUser(id = id, username = username, name = name, role = role)

fun LoginResponseDto.toDomainSession(nowEpochMillis: Long): Session = Session(
    token = token,
    expiresAtEpochMillis = nowEpochMillis + (expiresIn * 1000L),
    user = user.toDomain()
)

fun DonorInfo.toDto(): DonorDto? {
    if (isEmpty) return null
    return DonorDto(firstName = firstName, lastName = lastName, email = email, phone = phone)
}

fun PaymentIntentResponseDto.toDomain(): PaymentIntentInfo = PaymentIntentInfo(
    paymentIntentId = paymentIntentId,
    clientSecret = clientSecret,
    amount = amount,
    currency = currency
)

fun TransactionDto.toDomain(): TransactionRecord = TransactionRecord(
    transactionId = transactionId,
    givewpDonationId = givewpDonationId,
    amount = amount,
    currency = currency,
    donor = DonorInfo(
        firstName = donor.firstName,
        lastName = donor.lastName,
        email = donor.email,
        phone = donor.phone
    ),
    anonymous = donor.anonymous,
    stripePaymentIntentId = stripePaymentIntentId,
    stripeChargeId = stripeChargeId,
    status = status,
    createdAt = createdAt
)

fun TransactionDto.toEntity(fetchedAtEpochMillis: Long): TransactionEntity = TransactionEntity(
    transactionId = transactionId,
    givewpDonationId = givewpDonationId,
    amount = amount,
    currency = currency,
    firstName = donor.firstName,
    lastName = donor.lastName,
    email = donor.email,
    phone = donor.phone,
    anonymous = donor.anonymous,
    stripePaymentIntentId = stripePaymentIntentId,
    stripeChargeId = stripeChargeId,
    status = status,
    createdAt = createdAt,
    fetchedAtEpochMillis = fetchedAtEpochMillis
)

fun TransactionEntity.toDomain(): TransactionRecord = TransactionRecord(
    transactionId = transactionId,
    givewpDonationId = givewpDonationId,
    amount = amount,
    currency = currency,
    donor = DonorInfo(firstName = firstName, lastName = lastName, email = email, phone = phone),
    anonymous = anonymous,
    stripePaymentIntentId = stripePaymentIntentId,
    stripeChargeId = stripeChargeId,
    status = status,
    createdAt = createdAt
)
