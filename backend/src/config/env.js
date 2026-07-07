'use strict';

const path = require('path');
const dotenv = require('dotenv');

// Load backend/.env if present. Does not override variables already set in
// the environment (e.g. by the test harness), which is dotenv's default
// behaviour.
dotenv.config({ path: path.join(__dirname, '..', '..', '.env') });

function parseDurationToSeconds(value, fallbackSeconds) {
  if (value === undefined || value === null || value === '') {
    return fallbackSeconds;
  }
  if (/^\d+$/.test(String(value).trim())) {
    return parseInt(value, 10);
  }
  const match = /^(\d+)\s*(ms|s|m|h|d)$/i.exec(String(value).trim());
  if (!match) {
    return fallbackSeconds;
  }
  const amount = parseInt(match[1], 10);
  const unit = match[2].toLowerCase();
  const multipliers = { ms: 0.001, s: 1, m: 60, h: 3600, d: 86400 };
  return Math.round(amount * multipliers[unit]);
}

function parseOrigins(value) {
  if (!value) return [];
  return value
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
}

const env = process.env.NODE_ENV || 'development';

const config = {
  env,
  isTest: env === 'test',
  port: parseInt(process.env.PORT || '3000', 10),
  jwt: {
    secret: process.env.JWT_SECRET || 'dev-insecure-secret-change-me',
    expiresIn: process.env.JWT_EXPIRES_IN || '12h',
    expiresInSeconds: parseDurationToSeconds(process.env.JWT_EXPIRES_IN, 12 * 3600),
  },
  database: {
    path: process.env.DATABASE_PATH || path.join(__dirname, '..', '..', 'data', 'donation-terminal.sqlite'),
  },
  stripe: {
    secretKey: process.env.STRIPE_SECRET_KEY || '',
    webhookSecret: process.env.STRIPE_WEBHOOK_SECRET || '',
    terminalLocationId: process.env.STRIPE_TERMINAL_LOCATION_ID || '',
  },
  givewp: {
    apiBaseUrl: process.env.GIVEWP_API_BASE_URL || '',
    apiKey: process.env.GIVEWP_API_KEY || '',
    defaultFormId: process.env.GIVEWP_DEFAULT_FORM_ID || null,
  },
  defaultCurrency: (process.env.DEFAULT_CURRENCY || 'usd').toLowerCase(),
  cors: {
    allowedOrigins: parseOrigins(process.env.CORS_ALLOWED_ORIGINS),
  },
  logLevel: process.env.LOG_LEVEL || (env === 'test' ? 'silent' : 'info'),
  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000', 10),
    max: parseInt(process.env.RATE_LIMIT_MAX || '20', 10),
  },
};

module.exports = config;
