'use strict';

const rateLimit = require('express-rate-limit');
const config = require('../config/env');
const { AppError } = require('../utils/errors');
const { sendError } = require('../utils/response');

function buildLimiter(overrides = {}) {
  return rateLimit({
    windowMs: config.rateLimit.windowMs,
    max: config.rateLimit.max,
    standardHeaders: true,
    legacyHeaders: false,
    // Rate limiting would otherwise make integration tests that hammer an
    // endpoint flaky; the test suite sets NODE_ENV=test to disable it.
    skip: () => config.isTest,
    handler: (req, res) => {
      sendError(
        res,
        new AppError('RATE_LIMITED', 'Too many requests, please try again later.', 429)
      );
    },
    ...overrides,
  });
}

const loginLimiter = buildLimiter();
const paymentCreationLimiter = buildLimiter();

module.exports = { loginLimiter, paymentCreationLimiter };
