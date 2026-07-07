'use strict';

const logger = require('../utils/logger');
const { Errors } = require('../utils/errors');
const stripeService = require('./stripeService');
const givewpClient = require('./givewpClient');
const donationIntentsRepository = require('../db/repositories/donationIntentsRepository');
const transactionsRepository = require('../db/repositories/transactionsRepository');

/**
 * Idempotently completes a donation for a Stripe PaymentIntent that has
 * succeeded. This is the single code path used by both:
 *   - POST /api/v1/donations/complete (app-initiated)
 *   - the payment_intent.succeeded webhook (reconciliation backstop)
 *
 * Steps (per API_CONTRACT.md section 2):
 *   1. Verify the PaymentIntent's status is "succeeded" (caller supplies it,
 *      already re-fetched from Stripe, OR we fetch it ourselves).
 *   2. Load the stored donation_intents row for donor info/amount.
 *   3. If a transactions row already exists, return it unchanged.
 *   4. Otherwise call the GiveWP bridge, store a transactions row, return it.
 *
 * @param {object} params
 * @param {string} params.paymentIntentId
 * @param {string} [params.idempotencyKey] - falls back to the stored
 *   donation_intents.idempotency_key when the caller doesn't have one
 *   (e.g. the webhook path).
 * @param {object} [params.paymentIntent] - an already-fetched Stripe
 *   PaymentIntent object, to avoid a duplicate API call when the caller
 *   (the webhook handler) already has it from the event payload.
 */
async function completeDonation({ paymentIntentId, idempotencyKey, paymentIntent }) {
  // Idempotent short-circuit: if we already recorded this donation, return
  // it unchanged without touching Stripe or GiveWP again.
  const existingTransaction = transactionsRepository.findByPaymentIntentId(paymentIntentId);
  if (existingTransaction) {
    return existingTransaction;
  }

  let pi = paymentIntent;
  if (!pi) {
    pi = await stripeService.retrievePaymentIntent(paymentIntentId);
  }
  if (!pi) {
    throw Errors.intentNotFound(`No Stripe PaymentIntent found for ${paymentIntentId}`);
  }
  if (pi.status !== 'succeeded') {
    throw Errors.paymentNotSucceeded(
      `PaymentIntent ${paymentIntentId} has status "${pi.status}", expected "succeeded"`
    );
  }

  const intent = donationIntentsRepository.findByPaymentIntentId(paymentIntentId);
  if (!intent) {
    throw Errors.intentNotFound(
      `No donation_intents record found for PaymentIntent ${paymentIntentId}`
    );
  }

  // Re-check after the (possibly slow) Stripe round trip in case a
  // concurrent request already completed this donation.
  const raceCheck = transactionsRepository.findByPaymentIntentId(paymentIntentId);
  if (raceCheck) {
    return raceCheck;
  }

  const chargeId = extractChargeId(pi);
  const effectiveIdempotencyKey = idempotencyKey || intent.idempotencyKey;

  const givewpResult = await givewpClient.createDonation({
    amountMinorUnits: intent.amount,
    currency: intent.currency,
    firstName: intent.firstName,
    lastName: intent.lastName,
    email: intent.email,
    phone: intent.phone,
    anonymous: intent.anonymous,
    stripePaymentIntentId: paymentIntentId,
    stripeChargeId: chargeId,
  });

  const transaction = transactionsRepository.create({
    paymentIntentId,
    chargeId,
    givewpDonationId: givewpResult ? givewpResult.donationId : null,
    amount: intent.amount,
    currency: intent.currency,
    firstName: intent.firstName,
    lastName: intent.lastName,
    email: intent.email,
    phone: intent.phone,
    anonymous: intent.anonymous,
    status: 'completed',
    idempotencyKey: effectiveIdempotencyKey,
  });

  donationIntentsRepository.updateStatus(paymentIntentId, 'succeeded');

  logger.info(
    { paymentIntentId, transactionId: transaction.transactionId },
    'Donation completed and recorded in GiveWP'
  );

  return transaction;
}

function extractChargeId(paymentIntent) {
  if (!paymentIntent) return null;
  if (paymentIntent.latest_charge) {
    return typeof paymentIntent.latest_charge === 'string'
      ? paymentIntent.latest_charge
      : paymentIntent.latest_charge.id;
  }
  if (paymentIntent.charges && paymentIntent.charges.data && paymentIntent.charges.data[0]) {
    return paymentIntent.charges.data[0].id;
  }
  return null;
}

module.exports = { completeDonation };
