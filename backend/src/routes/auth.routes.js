'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const { validate } = require('../middleware/validate');
const { requireAuth } = require('../middleware/auth');
const { loginLimiter } = require('../middleware/rateLimit');
const { loginSchema } = require('../validators/schemas');
const authService = require('../services/authService');

const router = express.Router();

router.post(
  '/login',
  loginLimiter,
  validate(loginSchema),
  asyncHandler(async (req, res) => {
    const { username, password } = req.body;
    const result = await authService.login(username, password);
    return sendSuccess(res, result);
  })
);

router.post(
  '/logout',
  requireAuth,
  asyncHandler(async (req, res) => {
    authService.logout(req.tokenPayload);
    return sendSuccess(res, { loggedOut: true });
  })
);

module.exports = router;
