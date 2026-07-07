'use strict';

const config = require('../config/env');
const stripeService = require('./stripeService');
const donationIntentsRepository = require('../db/repositories/donationIntentsRepository');
const logger = require('../utils/logger');

/**
 * Creates a Stripe PaymentIntent for card_present/Terminal capture and
 * records a local donation_intents row. Idempotent on `idempotencyKey`:
 *   - The Stripe request itself carries the client's idempotencyKey, so a
 *     retry within Stripe's idempotency window returns the same
 *     PaymentIntent (same id, same client_secret) instead of creating a
 *     second one.
 *   - If the local INSERT then fails on the idempotency_key/payment_intent_id
 *     unique constraint (this exact request was already processed), the
 *     existing donation_intents row is returned instead of erroring.
 */
async function createPaymentIntent({ amount, currency, anonymous, donor, idempotencyKey }) {
  const existingByKey = donationIntentsRepository.findByIdempotencyKey(idempotencyKey);
  if (existingByKey) {
    const pi = await stripeService.retrievePaymentIntent(existingByKey.paymentIntentId);
    return {
      paymentIntentId: existingByKey.paymentIntentId,
      clientSecret: pi ? pi.client_secret : null,
      amount: existingByKey.amount,
      currency: existingByKey.currency,
    };
  }

  const effectiveCurrency = (currency || config.defaultCurrency).toLowerCase();

  const paymentIntent = await stripeService.createPaymentIntent({
    amount,
    currency: effectiveCurrency,
    idempotencyKey,
  });

  let intentRow;
  try {
    intentRow = donationIntentsRepository.create({
      paymentIntentId: paymentIntent.id,
      amount,
      currency: effectiveCurrency,
      firstName: donor && donor.firstName,
      lastName: donor && donor.lastName,
      email: donor && donor.email,
      phone: donor && donor.phone,
      anonymous: Boolean(anonymous),
      idempotencyKey,
    });
  } catch (err) {
    if (typeof err.code === 'string' && err.code.startsWith('SQLITE_CONSTRAINT')) {
      logger.warn(
        { paymentIntentId: paymentIntent.id },
        'donation_intents insert raced with a prior identical request; returning existing row'
      );
      intentRow =
        donationIntentsRepository.findByIdempotencyKey(idempotencyKey) ||
        donationIntentsRepository.findByPaymentIntentId(paymentIntent.id);
    } else {
      throw err;
    }
  }

  return {
    paymentIntentId: intentRow.paymentIntentId,
    clientSecret: paymentIntent.client_secret,
    amount: intentRow.amount,
    currency: intentRow.currency,
  };
}

module.exports = { createPaymentIntent };
