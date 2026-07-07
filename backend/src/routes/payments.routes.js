'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const { validate } = require('../middleware/validate');
const { requireAuth } = require('../middleware/auth');
const { paymentCreationLimiter } = require('../middleware/rateLimit');
const { paymentIntentSchema } = require('../validators/schemas');
const paymentsService = require('../services/paymentsService');

const router = express.Router();

router.post(
  '/intent',
  requireAuth,
  paymentCreationLimiter,
  validate(paymentIntentSchema),
  asyncHandler(async (req, res) => {
    const result = await paymentsService.createPaymentIntent(req.body);
    return sendSuccess(res, result);
  })
);

module.exports = router;
