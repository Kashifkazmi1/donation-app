'use strict';

require('./mocks/stripeMock');
const request = require('supertest');
const { createApp } = require('../src/app');
const { createUserAndLogin } = require('./helpers/testApp');

describe('POST /api/v1/auth/login', () => {
  let app;

  beforeAll(() => {
    app = createApp();
  });

  test('returns a token and user envelope on valid credentials', async () => {
    await createUserAndLogin(app, {
      username: 'loginok',
      password: 'correctpw123',
      name: 'Staff One',
      role: 'staff',
    });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ username: 'loginok', password: 'correctpw123' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(typeof res.body.data.token).toBe('string');
    expect(res.body.data.expiresIn).toBe(43200);
    expect(res.body.data.user).toMatchObject({
      username: 'loginok',
      name: 'Staff One',
      role: 'staff',
    });
    expect(typeof res.body.data.user.id).toBe('string');
  });

  test('rejects an invalid password with 401 INVALID_CREDENTIALS', async () => {
    await createUserAndLogin(app, { username: 'loginbad', password: 'correctpw123' });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ username: 'loginbad', password: 'totally-wrong' });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  test('rejects an unknown username with 401 INVALID_CREDENTIALS', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ username: 'does-not-exist', password: 'whatever123' });

    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  test('rejects a missing password with 400 VALIDATION_ERROR', async () => {
    const res = await request(app).post('/api/v1/auth/login').send({ username: 'x' });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});

describe('POST /api/v1/auth/logout', () => {
  let app;

  beforeAll(() => {
    app = createApp();
  });

  test('blacklists the token so it can no longer be used', async () => {
    const { token } = await createUserAndLogin(app, {
      username: 'logoutuser',
      password: 'password123',
    });

    const logoutRes = await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', `Bearer ${token}`);

    expect(logoutRes.status).toBe(200);
    expect(logoutRes.body.data.loggedOut).toBe(true);

    const reuseRes = await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', `Bearer ${token}`);

    expect(reuseRes.status).toBe(401);
  });

  test('requires authentication', async () => {
    const res = await request(app).post('/api/v1/auth/logout');
    expect(res.status).toBe(401);
  });
});
