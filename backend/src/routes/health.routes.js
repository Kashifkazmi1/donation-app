'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const config = require('../config/env');
const givewpClient = require('../services/givewpClient');

const router = express.Router();

router.get(
  '/',
  asyncHandler(async (req, res) => {
    const stripeConfigured = Boolean(config.stripe.secretKey);
    const givewp = await givewpClient.checkHealth();

    return sendSuccess(res, {
      status: 'ok',
      timestamp: new Date().toISOString(),
      stripe: { configured: stripeConfigured },
      givewp,
    });
  })
);

module.exports = router;
