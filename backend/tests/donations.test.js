'use strict';

const { stripeMock } = require('./mocks/stripeMock');
jest.mock('../src/services/givewpClient');

const request = require('supertest');
const { createApp } = require('../src/app');
const { createUserAndLogin } = require('./helpers/testApp');
const givewpClient = require('../src/services/givewpClient');
const donationIntentsRepository = require('../src/db/repositories/donationIntentsRepository');
const { Errors } = require('../src/utils/errors');

describe('POST /api/v1/donations/complete', () => {
  let app;
  let token;

  beforeAll(async () => {
    app = createApp();
    const result = await createUserAndLogin(app, { username: 'donuser', password: 'password123' });
    token = result.token;
  });

  beforeEach(() => {
    stripeMock.paymentIntents.retrieve.mockReset();
    givewpClient.createDonation.mockReset();
    givewpClient.createDonation.mockResolvedValue({ donationId: 999, donorId: 1, status: 'publish' });
  });

  test('is idempotent: calling twice returns the same transaction and only calls GiveWP once', async () => {
    const paymentIntentId = 'pi_complete_1';
    donationIntentsRepository.create({
      paymentIntentId,
      amount: 5000,
      currency: 'usd',
      firstName: 'Jane',
      lastName: 'Doe',
      email: 'jane@example.com',
      phone: '+15551234567',
      anonymous: false,
      idempotencyKey: 'aaaaaaa1-aaaa-4aaa-8aaa-aaaaaaaaaaa1',
    });
    stripeMock.paymentIntents.retrieve.mockResolvedValue({
      id: paymentIntentId,
      status: 'succeeded',
      latest_charge: 'ch_abc123',
    });

    const body = { paymentIntentId, idempotencyKey: 'bbbbbbb1-bbbb-4bbb-8bbb-bbbbbbbbbbb1' };

    const res1 = await request(app)
      .post('/api/v1/donations/complete')
      .set('Authorization', `Bearer ${token}`)
      .send(body);

    expect(res1.status).toBe(200);
    expect(res1.body.success).toBe(true);
    expect(res1.body.data).toMatchObject({
      givewpDonationId: 999,
      amount: 5000,
      currency: 'usd',
      donor: {
        firstName: 'Jane',
        lastName: 'Doe',
        email: 'jane@example.com',
        phone: '+15551234567',
        anonymous: false,
      },
      stripePaymentIntentId: paymentIntentId,
      stripeChargeId: 'ch_abc123',
      status: 'completed',
    });
    expect(typeof res1.body.data.transactionId).toBe('string');
    expect(typeof res1.body.data.createdAt).toBe('string');

    const res2 = await request(app)
      .post('/api/v1/donations/complete')
      .set('Authorization', `Bearer ${token}`)
      .send(body);

    expect(res2.status).toBe(200);
    expect(res2.body.data).toEqual(res1.body.data);

    expect(givewpClient.createDonation).toHaveBeenCalledTimes(1);
  });

  test('returns 409 PAYMENT_NOT_SUCCEEDED if the PaymentIntent has not succeeded', async () => {
    const paymentIntentId = 'pi_not_succeeded';
    donationIntentsRepository.create({
      paymentIntentId,
      amount: 1000,
      currency: 'usd',
      idempotencyKey: 'ccccccc1-cccc-4ccc-8ccc-ccccccccccc1',
    });
    stripeMock.paymentIntents.retrieve.mockResolvedValue({
      id: paymentIntentId,
      status: 'requires_payment_method',
    });

    const res = await request(app)
      .post('/api/v1/donations/complete')
      .set('Authorization', `Bearer ${token}`)
      .send({ paymentIntentId, idempotencyKey: 'ddddddd1-dddd-4ddd-8ddd-ddddddddddd1' });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('PAYMENT_NOT_SUCCEEDED');
    expect(givewpClient.createDonation).not.toHaveBeenCalled();
  });

  test('returns 404 INTENT_NOT_FOUND if no donation_intents row exists for the PaymentIntent', async () => {
    stripeMock.paymentIntents.retrieve.mockResolvedValue({
      id: 'pi_missing_intent',
      status: 'succeeded',
    });

    const res = await request(app)
      .post('/api/v1/donations/complete')
      .set('Authorization', `Bearer ${token}`)
      .send({ paymentIntentId: 'pi_missing_intent', idempotencyKey: 'eeeeeee1-eeee-4eee-8eee-eeeeeeeeeee1' });

    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('INTENT_NOT_FOUND');
  });

  test('returns 502 GIVEWP_ERROR when the GiveWP bridge call fails, without recording a transaction', async () => {
    const paymentIntentId = 'pi_givewp_fails';
    donationIntentsRepository.create({
      paymentIntentId,
      amount: 750,
      currency: 'usd',
      idempotencyKey: 'fffffff1-ffff-4fff-8fff-fffffffffff1',
    });
    stripeMock.paymentIntents.retrieve.mockResolvedValue({ id: paymentIntentId, status: 'succeeded' });
    givewpClient.createDonation.mockRejectedValue(Errors.givewpError('GiveWP down'));

    const res = await request(app)
      .post('/api/v1/donations/complete')
      .set('Authorization', `Bearer ${token}`)
      .send({ paymentIntentId, idempotencyKey: 'aaaaaaa2-aaaa-4aaa-8aaa-aaaaaaaaaaa2' });

    expect(res.status).toBe(502);
  });

  test('requires authentication', async () => {
    const res = await request(app)
      .post('/api/v1/donations/complete')
      .send({ paymentIntentId: 'pi_x', idempotencyKey: 'bbbbbbb2-bbbb-4bbb-8bbb-bbbbbbbbbbb2' });
    expect(res.status).toBe(401);
  });
});
