'use strict';

const request = require('supertest');
const { getDb } = require('../../src/db/connection');
const usersRepository = require('../../src/db/repositories/usersRepository');
const authService = require('../../src/services/authService');

/** Creates a staff user directly in the DB, then logs in via the real
 * /auth/login route to obtain a genuine JWT for use in other tests. */
async function createUserAndLogin(
  app,
  { username = 'staff1', password = 'password123', name = 'Staff One', role = 'staff' } = {}
) {
  getDb();
  const passwordHash = await authService.hashPassword(password);
  usersRepository.create({ username, passwordHash, name, role });

  const response = await request(app)
    .post('/api/v1/auth/login')
    .send({ username, password });

  return { token: response.body.data.token, user: response.body.data.user, response };
}

module.exports = { createUserAndLogin };
