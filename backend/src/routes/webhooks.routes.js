'use strict';

const express = require('express');
const logger = require('../utils/logger');
const stripeService = require('../services/stripeService');
const donationService = require('../services/donationService');
const donationIntentsRepository = require('../db/repositories/donationIntentsRepository');
const transactionsRepository = require('../db/repositories/transactionsRepository');

const router = express.Router();

// IMPORTANT: this route needs the *raw* request body to verify the
// Stripe-Signature header. It must be mounted before the global
// express.json() body parser (see app.js) — this express.raw() call here
// is what actually captures the raw bytes for this path.
router.post(
  '/stripe',
  express.raw({ type: 'application/json' }),
  async (req, res) => {
    const signature = req.headers['stripe-signature'];
    let event;

    try {
      event = stripeService.constructWebhookEvent(req.body, signature);
    } catch (err) {
      logger.warn({ err: err.message }, 'Stripe webhook signature verification failed');
      return res.status(400).json({
        success: false,
        error: { code: 'INVALID_SIGNATURE', message: 'Webhook signature verification failed' },
      });
    }

    // Per the contract, all webhook handlers respond 200 quickly; failures
    // are logged, not retried synchronously. So we always ACK with 200 once
    // the signature is valid, regardless of how downstream processing goes.
    res.status(200).json({ success: true, data: { received: true } });

    try {
      await handleEvent(event);
    } catch (err) {
      logger.error(
        { err: err.message, eventType: event.type, eventId: event.id },
        'Stripe webhook handler failed'
      );
    }
  }
);

async function handleEvent(event) {
  switch (event.type) {
    case 'payment_intent.succeeded': {
      const paymentIntent = event.data.object;
      const existing = transactionsRepository.findByPaymentIntentId(paymentIntent.id);
      if (existing) {
        logger.info(
          { paymentIntentId: paymentIntent.id },
          'payment_intent.succeeded: donation already recorded, no action needed'
        );
        return;
      }
      logger.info(
        { paymentIntentId: paymentIntent.id },
        'payment_intent.succeeded: reconciling via completeDonation'
      );
      await donationService.completeDonation({
        paymentIntentId: paymentIntent.id,
        paymentIntent,
      });
      return;
    }

    case 'payment_intent.payment_failed': {
      const paymentIntent = event.data.object;
      const intent = donationIntentsRepository.findByPaymentIntentId(paymentIntent.id);
      if (intent) {
        donationIntentsRepository.updateStatus(paymentIntent.id, 'failed');
        logger.info({ paymentIntentId: paymentIntent.id }, 'Marked donation_intents row failed');
      } else {
        logger.warn(
          { paymentIntentId: paymentIntent.id },
          'payment_intent.payment_failed for unknown donation_intents row'
        );
      }
      return;
    }

    case 'terminal.reader.action_succeeded':
      logger.info({ reader: event.data.object.id }, 'terminal.reader.action_succeeded');
      return;

    case 'terminal.reader.action_failed':
      logger.warn({ reader: event.data.object.id }, 'terminal.reader.action_failed');
      return;

    default:
      logger.debug({ eventType: event.type }, 'Unhandled Stripe webhook event type');
  }
}

module.exports = router;
