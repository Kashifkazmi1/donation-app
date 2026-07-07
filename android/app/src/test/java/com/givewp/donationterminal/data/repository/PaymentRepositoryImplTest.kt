package com.givewp.donationterminal.data.repository

import com.givewp.donationterminal.data.local.db.TransactionDao
import com.givewp.donationterminal.data.remote.NetworkCallExecutor
import com.givewp.donationterminal.data.remote.api.PaymentsApi
import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.CompleteDonationRequestDto
import com.givewp.donationterminal.data.remote.dto.CreatePaymentIntentRequestDto
import com.givewp.donationterminal.data.remote.dto.PaymentIntentResponseDto
import com.givewp.donationterminal.data.remote.dto.TransactionDonorDto
import com.givewp.donationterminal.data.remote.dto.TransactionDto
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.DonorInfo
import com.squareup.moshi.Moshi
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaymentRepositoryImplTest {

    private lateinit var paymentsApi: PaymentsApi
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: PaymentRepositoryImpl

    @Before
    fun setUp() {
        paymentsApi = mockk()
        transactionDao = mockk(relaxed = true)
        val networkCallExecutor = NetworkCallExecutor(Moshi.Builder().build())
        repository = PaymentRepositoryImpl(paymentsApi, networkCallExecutor, transactionDao)
    }

    @Test
    fun `two drafts generate distinct idempotency keys by default`() {
        val draftA = DonationDraft(amountCents = 1000, currency = "usd", anonymous = false, donor = DonorInfo())
        val draftB = DonationDraft(amountCents = 1000, currency = "usd", anonymous = false, donor = DonorInfo())
        assertNotEquals(draftA.idempotencyKey, draftB.idempotencyKey)
    }

    @Test
    fun `createPaymentIntent forwards the draft's idempotency key verbatim`() = runTest {
        val draft = DonationDraft(
            amountCents = 2500,
            currency = "usd",
            anonymous = false,
            donor = DonorInfo(firstName = "Jane"),
            idempotencyKey = "fixed-key-123"
        )
        val requestSlot = mutableListOf<CreatePaymentIntentRequestDto>()
        coEvery { paymentsApi.createPaymentIntent(capture(requestSlot)) } returns Response.success(
            ApiEnvelope(
                success = true,
                data = PaymentIntentResponseDto("pi_1", "pi_1_secret", 2500, "usd")
            )
        )

        // Simulate calling createPaymentIntent twice for the *same* draft, as the ViewModel would
        // if the first backend call timed out and the user retried before any response arrived.
        repository.createPaymentIntent(draft)
        repository.createPaymentIntent(draft)

        assertEquals(2, requestSlot.size)
        assertEquals("fixed-key-123", requestSlot[0].idempotencyKey)
        assertEquals("fixed-key-123", requestSlot[1].idempotencyKey)
        coVerify(exactly = 2) { paymentsApi.createPaymentIntent(any()) }
    }

    @Test
    fun `completeDonation forwards the caller-supplied idempotency key`() = runTest {
        val requestSlot = mutableListOf<CompleteDonationRequestDto>()
        coEvery { paymentsApi.completeDonation(capture(requestSlot)) } returns Response.success(
            ApiEnvelope(
                success = true,
                data = TransactionDto(
                    transactionId = "txn_1",
                    givewpDonationId = 1L,
                    amount = 2500,
                    currency = "usd",
                    donor = TransactionDonorDto(firstName = "Jane"),
                    stripePaymentIntentId = "pi_1",
                    stripeChargeId = "ch_1",
                    status = "completed",
                    createdAt = "2026-07-07T12:00:00.000Z"
                )
            )
        )

        repository.completeDonation("pi_1", "fixed-key-123")
        repository.completeDonation("pi_1", "fixed-key-123")

        assertEquals(2, requestSlot.size)
        assertEquals("fixed-key-123", requestSlot[0].idempotencyKey)
        assertEquals("fixed-key-123", requestSlot[1].idempotencyKey)
        assertEquals("pi_1", requestSlot[0].paymentIntentId)
    }
}
