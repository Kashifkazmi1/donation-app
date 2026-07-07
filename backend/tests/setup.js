'use strict';

// Environment for the whole test run. Set before any application module is
// required so src/config/env.js picks these up (dotenv won't override
// already-set env vars).
process.env.NODE_ENV = 'test';
process.env.DATABASE_PATH = ':memory:';
process.env.JWT_SECRET = 'test-jwt-secret';
process.env.JWT_EXPIRES_IN = '12h';
process.env.STRIPE_SECRET_KEY = 'sk_test_fake';
process.env.STRIPE_WEBHOOK_SECRET = 'whsec_test_fake';
process.env.STRIPE_TERMINAL_LOCATION_ID = 'tml_test_fake';
process.env.GIVEWP_API_BASE_URL = 'https://wp.example.test/wp-json/donation-terminal/v1';
process.env.GIVEWP_API_KEY = 'givewp_test_fake';
process.env.GIVEWP_DEFAULT_FORM_ID = '1';
process.env.DEFAULT_CURRENCY = 'usd';
process.env.CORS_ALLOWED_ORIGINS = '';
process.env.LOG_LEVEL = 'silent';
process.env.RATE_LIMIT_WINDOW_MS = '900000';
process.env.RATE_LIMIT_MAX = '1000';
