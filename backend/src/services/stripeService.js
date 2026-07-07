'use strict';

const Stripe = require('stripe');
const config = require('../config/env');
const logger = require('../utils/logger');
const { Errors } = require('../utils/errors');

let stripeClient;

function getStripeClient() {
  if (!stripeClient) {
    stripeClient = new Stripe(config.stripe.secretKey, {
      apiVersion: '2024-06-20',
    });
  }
  return stripeClient;
}

async function createConnectionToken() {
  const stripe = getStripeClient();
  try {
    const connectionToken = await stripe.terminal.connectionTokens.create();
    return connectionToken;
  } catch (err) {
    logger.error({ err: err.message }, 'Stripe connection token creation failed');
    throw Errors.stripeError('Failed to create Stripe Terminal connection token');
  }
}

async function listReaders() {
  const stripe = getStripeClient();
  const locationId = config.stripe.terminalLocationId;
  try {
    const [readers, location] = await Promise.all([
      stripe.terminal.readers.list(locationId ? { location: locationId, limit: 100 } : { limit: 100 }),
      locationId ? stripe.terminal.locations.retrieve(locationId) : Promise.resolve(null),
    ]);
    return { readers: readers.data, location };
  } catch (err) {
    logger.error({ err: err.message }, 'Stripe reader listing failed');
    throw Errors.stripeError('Failed to list Stripe Terminal readers');
  }
}

async function createPaymentIntent({ amount, currency, idempotencyKey, metadata }) {
  const stripe = getStripeClient();
  try {
    const paymentIntent = await stripe.paymentIntents.create(
      {
        amount,
        currency,
        payment_method_types: ['card_present'],
        capture_method: 'automatic',
        metadata: metadata || {},
      },
      idempotencyKey ? { idempotencyKey } : undefined
    );
    return paymentIntent;
  } catch (err) {
    logger.error({ err: err.message }, 'Stripe PaymentIntent creation failed');
    throw Errors.stripeError('Failed to create Stripe PaymentIntent', {
      stripeMessage: err.message,
    });
  }
}

async function retrievePaymentIntent(paymentIntentId) {
  const stripe = getStripeClient();
  try {
    const paymentIntent = await stripe.paymentIntents.retrieve(paymentIntentId);
    return paymentIntent;
  } catch (err) {
    logger.warn({ err: err.message, paymentIntentId }, 'Stripe PaymentIntent retrieval failed');
    return null;
  }
}

function constructWebhookEvent(rawBody, signature) {
  const stripe = getStripeClient();
  return stripe.webhooks.constructEvent(rawBody, signature, config.stripe.webhookSecret);
}

module.exports = {
  getStripeClient,
  createConnectionToken,
  listReaders,
  createPaymentIntent,
  retrievePaymentIntent,
  constructWebhookEvent,
};
