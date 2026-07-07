'use strict';

const axios = require('axios');
const config = require('../config/env');
const logger = require('../utils/logger');
const { Errors } = require('../utils/errors');
const { minorUnitsToDecimalString, toUpperCurrency } = require('../utils/money');

function getBaseUrl() {
  return String(config.givewp.apiBaseUrl || '').replace(/\/+$/, '');
}

function client() {
  return axios.create({
    baseURL: getBaseUrl(),
    timeout: 10000,
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': config.givewp.apiKey,
    },
  });
}

/**
 * Creates (or, if already present for this PaymentIntent, fetches) a GiveWP
 * donation via the WordPress bridge plugin. See API_CONTRACT.md section 4.
 */
async function createDonation({
  amountMinorUnits,
  currency,
  firstName,
  lastName,
  email,
  phone,
  anonymous,
  stripePaymentIntentId,
  stripeChargeId,
  formId,
  date,
}) {
  const payload = {
    amount: minorUnitsToDecimalString(amountMinorUnits),
    currency: toUpperCurrency(currency),
    firstName: firstName || undefined,
    lastName: lastName || undefined,
    email: email || undefined,
    phone: phone || undefined,
    anonymous: Boolean(anonymous),
    gateway: 'stripe_terminal',
    status: 'publish',
    stripePaymentIntentId,
    stripeChargeId: stripeChargeId || undefined,
    stripeTransactionId: stripeChargeId || undefined,
    formId: formId != null ? formId : config.givewp.defaultFormId,
    date: date || new Date().toISOString(),
  };

  try {
    const response = await client().post('/donations', payload);
    return response.data && response.data.data ? response.data.data : response.data;
  } catch (err) {
    const status = err.response ? err.response.status : undefined;
    const body = err.response ? err.response.data : undefined;
    logger.error(
      { status, body, stripePaymentIntentId },
      'GiveWP bridge donation creation failed'
    );
    throw Errors.givewpError('Failed to create donation in GiveWP', { status, body });
  }
}

async function checkHealth() {
  try {
    const response = await client().get('/health', { timeout: 5000 });
    const data = response.data && response.data.data ? response.data.data : response.data;
    return { reachable: true, ...data };
  } catch (err) {
    logger.warn({ err: err.message }, 'GiveWP bridge health check failed');
    return { reachable: false };
  }
}

module.exports = { createDonation, checkHealth };
