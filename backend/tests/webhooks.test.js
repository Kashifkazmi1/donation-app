'use strict';

const { stripeMock } = require('./mocks/stripeMock');
const request = require('supertest');
const { createApp } = require('../src/app');

describe('POST /api/v1/webhooks/stripe', () => {
  let app;

  beforeAll(() => {
    app = createApp();
  });

  beforeEach(() => {
    stripeMock.webhooks.constructEvent.mockReset();
  });

  test('rejects a request with an invalid Stripe-Signature with 400 INVALID_SIGNATURE', async () => {
    stripeMock.webhooks.constructEvent.mockImplementation(() => {
      throw new Error('No signatures found matching the expected signature for payload');
    });

    const res = await request(app)
      .post('/api/v1/webhooks/stripe')
      .set('Content-Type', 'application/json')
      .set('Stripe-Signature', 'bad-signature')
      .send(JSON.stringify({ id: 'evt_test_bad', type: 'payment_intent.succeeded' }));

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('INVALID_SIGNATURE');
    expect(stripeMock.webhooks.constructEvent).toHaveBeenCalledTimes(1);
  });

  test('rejects a request with a missing Stripe-Signature header', async () => {
    stripeMock.webhooks.constructEvent.mockImplementation(() => {
      throw new Error('Missing stripe-signature header');
    });

    const res = await request(app)
      .post('/api/v1/webhooks/stripe')
      .set('Content-Type', 'application/json')
      .send(JSON.stringify({ id: 'evt_test_missing' }));

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('INVALID_SIGNATURE');
  });

  test('accepts a validly-signed event and responds 200 immediately', async () => {
    stripeMock.webhooks.constructEvent.mockReturnValue({
      id: 'evt_test_good',
      type: 'terminal.reader.action_succeeded',
      data: { object: { id: 'tmr_123' } },
    });

    const res = await request(app)
      .post('/api/v1/webhooks/stripe')
      .set('Content-Type', 'application/json')
      .set('Stripe-Signature', 'good-signature')
      .send(JSON.stringify({ id: 'evt_test_good' }));

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});
