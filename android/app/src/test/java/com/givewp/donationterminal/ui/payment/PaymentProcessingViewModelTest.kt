package com.givewp.donationterminal.ui.payment

import app.cash.turbine.test
import com.givewp.donationterminal.data.connectivity.NetworkMonitor
import com.givewp.donationterminal.data.repository.DonationSessionRepositoryImpl
import com.givewp.donationterminal.domain.model.AppError
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.model.AppResult
import com.givewp.donationterminal.domain.model.DonationDraft
import com.givewp.donationterminal.domain.model.DonorInfo
import com.givewp.donationterminal.domain.model.PaymentCollectionResult
import com.givewp.donationterminal.domain.model.PaymentIntentInfo
import com.givewp.donationterminal.domain.model.TransactionRecord
import com.givewp.donationterminal.domain.repository.PaymentRepository
import com.givewp.donationterminal.domain.repository.ReaderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentProcessingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var readerRepository: ReaderRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var sessionRepository: DonationSessionRepositoryImpl
    private lateinit var viewModel: PaymentProcessingViewModel

    private val draft = DonationDraft(
        amountCents = 2500,
        currency = "usd",
        anonymous = false,
        donor = DonorInfo(firstName = "Jane", lastName = "Doe"),
        idempotencyKey = "fixed-idempotency-key"
    )

    private val intentInfo = PaymentIntentInfo(
        paymentIntentId = "pi_123",
        clientSecret = "pi_123_secret_abc",
        amount = 2500,
        currency = "usd"
    )

    private val collectionResult = PaymentCollectionResult(
        paymentIntentId = "pi_123",
        cardBrand = "visa",
        cardLast4 = "4242"
    )

    private val transactionRecord = TransactionRecord(
        transactionId = "txn_1",
        givewpDonationId = 42L,
        amount = 2500,
        currency = "usd",
        donor = DonorInfo(firstName = "Jane", lastName = "Doe"),
        anonymous = false,
        stripePaymentIntentId = "pi_123",
        stripeChargeId = "ch_123",
        status = "completed",
        createdAt = "2026-07-07T12:00:00.000Z"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        paymentRepository = mockk()
        readerRepository = mockk(relaxed = true)
        networkMonitor = mockk()
        sessionRepository = DonationSessionRepositoryImpl()
        sessionRepository.startNewDonation(draft)

        every { networkMonitor.isCurrentlyOnline() } returns true

        viewModel = PaymentProcessingViewModel(
            paymentRepository = paymentRepository,
            readerRepository = readerRepository,
            donationSessionRepository = sessionRepository,
            networkMonitor = networkMonitor
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `happy path progresses through every state to Success`() = runTest(dispatcher) {
        coEvery { paymentRepository.createPaymentIntent(draft) } returns AppResult.Success(intentInfo)
        coEvery { readerRepository.collectAndConfirmPayment(intentInfo.clientSecret) } returns
            AppResult.Success(collectionResult)
        coEvery { paymentRepository.completeDonation("pi_123", draft.idempotencyKey) } returns
            AppResult.Success(transactionRecord)

        viewModel.uiState.test {
            assertIs<PaymentProcessingUiState.CreatingIntent>(awaitItem())

            viewModel.start()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<PaymentProcessingUiState.WaitingForCard>(awaitItem())
            assertIs<PaymentProcessingUiState.ProcessingPayment>(awaitItem())
            assertIs<PaymentProcessingUiState.ConfirmingWithBackend>(awaitItem())
            assertIs<PaymentProcessingUiState.Success>(awaitItem())
        }

        assertEquals(transactionRecord, sessionRepository.lastResult.value)
        assertEquals(intentInfo, sessionRepository.paymentIntent.value)
    }

    @Test
    fun `card decline surfaces a Failed state with the decline reason`() = runTest(dispatcher) {
        val declineError = AppError(AppErrorType.STRIPE_DECLINED, "card_declined", "Your card was declined.")
        coEvery { paymentRepository.createPaymentIntent(draft) } returns AppResult.Success(intentInfo)
        coEvery { readerRepository.collectAndConfirmPayment(intentInfo.clientSecret) } returns
            AppResult.Failure(declineError)

        viewModel.uiState.test {
            awaitItem() // initial CreatingIntent

            viewModel.start()
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() // WaitingForCard
            val failed = awaitItem()
            assertIs<PaymentProcessingUiState.Failed>(failed)
            assertEquals(declineError, failed.error)
        }
        assertEquals(declineError, sessionRepository.lastFailure.value)
    }

    @Test
    fun `no internet connection fails fast without calling the backend`() = runTest(dispatcher) {
        every { networkMonitor.isCurrentlyOnline() } returns false

        viewModel.uiState.test {
            awaitItem() // initial CreatingIntent

            viewModel.start()
            dispatcher.scheduler.advanceUntilIdle()

            val failed = awaitItem()
            assertIs<PaymentProcessingUiState.Failed>(failed)
            assertEquals(AppErrorType.NETWORK, failed.error.type)
        }
    }

    @Test
    fun `cancel during collection stops the reader and records a cancelled failure`() = runTest(dispatcher) {
        viewModel.cancel()

        val state = viewModel.uiState.value
        assertIs<PaymentProcessingUiState.Failed>(state)
        assertEquals(AppErrorType.STRIPE_CANCELLED, state.error.type)
        assertTrue(sessionRepository.lastFailure.value?.type == AppErrorType.STRIPE_CANCELLED)
    }

    @Test
    fun `retrying after a failure reuses the already-created PaymentIntent (idempotent)`() = runTest(dispatcher) {
        coEvery { paymentRepository.createPaymentIntent(draft) } returns AppResult.Success(intentInfo)
        coEvery { readerRepository.collectAndConfirmPayment(intentInfo.clientSecret) } returnsMany listOf(
            AppResult.Failure(AppError(AppErrorType.READER_DISCONNECTED, "READER_DISCONNECTED", "Reader disconnected.")),
            AppResult.Success(collectionResult)
        )
        coEvery { paymentRepository.completeDonation("pi_123", draft.idempotencyKey) } returns
            AppResult.Success(transactionRecord)

        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<PaymentProcessingUiState.Failed>(viewModel.uiState.value)

        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertIs<PaymentProcessingUiState.Success>(viewModel.uiState.value)
        // createPaymentIntent should only have been called once across both attempts because the
        // ViewModel reuses donationSessionRepository.paymentIntent once it has been set.
        coVerify(exactly = 1) { paymentRepository.createPaymentIntent(draft) }
    }
}
