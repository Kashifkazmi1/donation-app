'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const { validate } = require('../middleware/validate');
const { requireAuth } = require('../middleware/auth');
const { donationCompleteSchema } = require('../validators/schemas');
const donationService = require('../services/donationService');

const router = express.Router();

router.post(
  '/complete',
  requireAuth,
  validate(donationCompleteSchema),
  asyncHandler(async (req, res) => {
    const { paymentIntentId, idempotencyKey } = req.body;
    const transaction = await donationService.completeDonation({
      paymentIntentId,
      idempotencyKey,
    });
    return sendSuccess(res, transaction);
  })
);

module.exports = router;
