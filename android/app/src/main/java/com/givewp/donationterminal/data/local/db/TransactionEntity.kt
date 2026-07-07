package com.givewp.donationterminal.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of the backend's transaction shape -- mirrors /transactions and /donations/complete. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val givewpDonationId: Long?,
    val amount: Long,
    val currency: String,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phone: String?,
    val anonymous: Boolean,
    val stripePaymentIntentId: String,
    val stripeChargeId: String?,
    val status: String,
    val createdAt: String,
    /** Local-only bookkeeping: which remote page this row was last seen on, for pagination. */
    val fetchedAtEpochMillis: Long
)
