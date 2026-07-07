'use strict';

// Mocks the `stripe` npm package so no test ever hits the real Stripe API.
// Import this file FIRST (before requiring src/app or any service) in any
// test file that needs it, so the mock is registered before stripeService
// requires 'stripe'.
jest.mock('stripe', () => {
  const mockInstance = {
    paymentIntents: {
      create: jest.fn(),
      retrieve: jest.fn(),
    },
    terminal: {
      connectionTokens: { create: jest.fn() },
      readers: { list: jest.fn().mockResolvedValue({ data: [] }) },
      locations: { retrieve: jest.fn() },
    },
    webhooks: {
      constructEvent: jest.fn(),
    },
  };
  const StripeMock = jest.fn(() => mockInstance);
  StripeMock.__mockInstance = mockInstance;
  return StripeMock;
});

const Stripe = require('stripe');

module.exports = { stripeMock: Stripe.__mockInstance };
