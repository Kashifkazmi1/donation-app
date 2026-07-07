'use strict';

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const pinoHttp = require('pino-http');

const config = require('./config/env');
const logger = require('./utils/logger');
const { errorHandler, notFoundHandler } = require('./middleware/errorHandler');

const authRoutes = require('./routes/auth.routes');
const terminalRoutes = require('./routes/terminal.routes');
const paymentsRoutes = require('./routes/payments.routes');
const donationsRoutes = require('./routes/donations.routes');
const transactionsRoutes = require('./routes/transactions.routes');
const healthRoutes = require('./routes/health.routes');
const webhooksRoutes = require('./routes/webhooks.routes');

function createApp() {
  const app = express();

  app.disable('x-powered-by');
  app.use(helmet());

  app.use(
    cors({
      origin(origin, callback) {
        // Allow same-origin/non-browser requests (no Origin header at all),
        // and any origin explicitly listed in CORS_ALLOWED_ORIGINS.
        if (!origin || config.cors.allowedOrigins.length === 0) {
          return callback(null, true);
        }
        if (config.cors.allowedOrigins.includes(origin)) {
          return callback(null, true);
        }
        return callback(new Error('Not allowed by CORS'));
      },
    })
  );

  app.use(
    pinoHttp({
      logger,
      autoLogging: !config.isTest,
      redact: {
        paths: ['req.headers.authorization', 'req.headers["stripe-signature"]', 'req.headers["x-api-key"]'],
        censor: '[REDACTED]',
      },
    })
  );

  // The Stripe webhook route needs the raw request body to verify the
  // signature, so it is mounted (with its own express.raw() parser, see
  // webhooks.routes.js) BEFORE the global express.json() parser below.
  // This route handles and sends its own response, so it never reaches
  // express.json().
  app.use('/api/v1/webhooks', webhooksRoutes);

  app.use(express.json({ limit: '1mb' }));

  app.use('/api/v1/auth', authRoutes);
  app.use('/api/v1/terminal', terminalRoutes);
  app.use('/api/v1/payments', paymentsRoutes);
  app.use('/api/v1/donations', donationsRoutes);
  app.use('/api/v1/transactions', transactionsRoutes);
  app.use('/api/v1/health', healthRoutes);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };
