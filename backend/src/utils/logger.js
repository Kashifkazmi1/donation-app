'use strict';

const pino = require('pino');
const config = require('../config/env');

// Never log secrets: JWTs, PaymentIntent client secrets, Stripe signatures,
// API keys, or passwords. `pino` redaction blanks these paths wherever they
// appear in logged objects.
const logger = pino({
  level: config.logLevel,
  redact: {
    paths: [
      'req.headers.authorization',
      'req.headers["stripe-signature"]',
      'req.headers["x-api-key"]',
      'req.body.password',
      'req.body.clientSecret',
      'req.body.client_secret',
      '*.clientSecret',
      '*.client_secret',
      '*.password',
      '*.password_hash',
      '*.apiKey',
      '*.api_key',
      '*.token',
      '*.webhookSecret',
    ],
    censor: '[REDACTED]',
  },
});

module.exports = logger;
