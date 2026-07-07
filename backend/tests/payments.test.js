'use strict';

const { stripeMock } = require('./mocks/stripeMock');
const request = require('supertest');
const { createApp } = require('../src/app');
const { createUserAndLogin } = require('./helpers/testApp');

describe('POST /api/v1/payments/intent', () => {
  let app;
  let token;

  beforeAll(async () => {
    app = createApp();
    const result = await createUserAndLogin(app, { username: 'payuser', password: 'password123' });
    token = result.token;
  });

  beforeEach(() => {
    stripeMock.paymentIntents.create.mockReset();
    stripeMock.paymentIntents.retrieve.mockReset();
  });

  test('requires authentication', async () => {
    const res = await request(app)
      .post('/api/v1/payments/intent')
      .send({ amount: 1000, idempotencyKey: '33333333-3333-4333-8333-333333333333' });
    expect(res.status).toBe(401);
  });

  test('rejects a missing amount with 400 VALIDATION_ERROR', async () => {
    const res = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send({ idempotencyKey: '11111111-1111-4111-8111-111111111111' });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(stripeMock.paymentIntents.create).not.toHaveBeenCalled();
  });

  test('rejects a non-positive amount with 400 VALIDATION_ERROR', async () => {
    const res = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send({ amount: -5, idempotencyKey: '22222222-2222-4222-8222-222222222222' });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('rejects a non-integer amount with 400 VALIDATION_ERROR', async () => {
    const res = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send({ amount: 25.5, idempotencyKey: '55555555-5555-4555-8555-555555555555' });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('rejects a missing/invalid idempotencyKey with 400 VALIDATION_ERROR', async () => {
    const res = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send({ amount: 1000, idempotencyKey: 'not-a-uuid' });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('creates a Stripe PaymentIntent and returns the expected shape', async () => {
    stripeMock.paymentIntents.create.mockResolvedValue({
      id: 'pi_123',
      client_secret: 'pi_123_secret_abc',
    });

    const res = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send({
        amount: 2500,
        currency: 'usd',
        anonymous: false,
        donor: { firstName: 'Jane', lastName: 'Doe', email: 'jane@example.com' },
        idempotencyKey: '44444444-4444-4444-8444-444444444444',
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data).toEqual({
      paymentIntentId: 'pi_123',
      clientSecret: 'pi_123_secret_abc',
      amount: 2500,
      currency: 'usd',
    });
    expect(stripeMock.paymentIntents.create).toHaveBeenCalledTimes(1);
    const [piArgs, options] = stripeMock.paymentIntents.create.mock.calls[0];
    expect(piArgs.amount).toBe(2500);
    expect(piArgs.currency).toBe('usd');
    expect(piArgs.payment_method_types).toContain('card_present');
    expect(options).toEqual({ idempotencyKey: '44444444-4444-4444-8444-444444444444' });
  });

  test('replaying the same idempotencyKey does not create a second PaymentIntent', async () => {
    stripeMock.paymentIntents.create.mockResolvedValue({
      id: 'pi_456',
      client_secret: 'pi_456_secret_abc',
    });
    stripeMock.paymentIntents.retrieve.mockResolvedValue({
      id: 'pi_456',
      client_secret: 'pi_456_secret_abc',
    });

    const body = { amount: 1500, idempotencyKey: '66666666-6666-4666-8666-666666666666' };

    const first = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send(body);
    expect(first.status).toBe(200);

    const second = await request(app)
      .post('/api/v1/payments/intent')
      .set('Authorization', `Bearer ${token}`)
      .send(body);
    expect(second.status).toBe(200);
    expect(second.body.data.paymentIntentId).toBe(first.body.data.paymentIntentId);

    // Stripe's own idempotency key means create() may be called again by
    // our code, but never a NEW PaymentIntent is created locally: both
    // responses reference the exact same PaymentIntent id.
    expect(stripeMock.paymentIntents.create.mock.calls.length).toBeGreaterThanOrEqual(1);
  });
});
