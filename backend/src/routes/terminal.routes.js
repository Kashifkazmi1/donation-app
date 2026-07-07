'use strict';

const express = require('express');
const { asyncHandler } = require('../utils/asyncHandler');
const { sendSuccess } = require('../utils/response');
const { requireAuth } = require('../middleware/auth');
const stripeService = require('../services/stripeService');

const router = express.Router();

router.post(
  '/connection-token',
  requireAuth,
  asyncHandler(async (req, res) => {
    const connectionToken = await stripeService.createConnectionToken();
    return sendSuccess(res, { secret: connectionToken.secret });
  })
);

router.get(
  '/reader-status',
  requireAuth,
  asyncHandler(async (req, res) => {
    const { readers, location } = await stripeService.listReaders();
    return sendSuccess(res, {
      location: location
        ? { id: location.id, displayName: location.display_name }
        : null,
      readers: readers.map((reader) => ({
        id: reader.id,
        label: reader.label,
        serialNumber: reader.serial_number,
        deviceType: reader.device_type,
        status: reader.status,
        batteryLevel: reader.battery_level ?? null,
        locationId: reader.location,
      })),
    });
  })
);

module.exports = router;
