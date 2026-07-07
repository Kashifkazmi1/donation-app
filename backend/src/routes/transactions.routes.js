'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const { validate } = require('../middleware/validate');
const { requireAuth } = require('../middleware/auth');
const { transactionsQuerySchema } = require('../validators/schemas');
const transactionsRepository = require('../db/repositories/transactionsRepository');

const router = express.Router();

router.get(
  '/',
  requireAuth,
  validate(transactionsQuerySchema, 'query'),
  asyncHandler(async (req, res) => {
    const { page, pageSize, status } = req.query;
    const result = transactionsRepository.list({ page, pageSize, status });
    return sendSuccess(res, result);
  })
);

module.exports = router;
