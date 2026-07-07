'use strict';

const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const config = require('../config/env');
const usersRepository = require('../db/repositories/usersRepository');
const tokenBlacklistRepository = require('../db/repositories/tokenBlacklistRepository');
const { Errors } = require('../utils/errors');

const BCRYPT_ROUNDS = 12;

async function hashPassword(password) {
  return bcrypt.hash(password, BCRYPT_ROUNDS);
}

async function verifyPassword(password, hash) {
  return bcrypt.compare(password, hash);
}

function issueToken(user) {
  const jti = crypto.randomUUID();
  const payload = {
    sub: String(user.id),
    username: user.username,
    name: user.name,
    role: user.role,
  };
  const token = jwt.sign(payload, config.jwt.secret, {
    expiresIn: config.jwt.expiresIn,
    jwtid: jti,
  });
  return { token, jti, expiresIn: config.jwt.expiresInSeconds };
}

async function login(username, password) {
  const user = usersRepository.findByUsername(username);
  if (!user) {
    throw Errors.invalidCredentials();
  }
  const valid = await verifyPassword(password, user.password_hash);
  if (!valid) {
    throw Errors.invalidCredentials();
  }
  const { token, expiresIn } = issueToken(user);
  return {
    token,
    expiresIn,
    user: {
      id: String(user.id),
      username: user.username,
      name: user.name,
      role: user.role,
    },
  };
}

function verifyToken(token) {
  return jwt.verify(token, config.jwt.secret);
}

function logout(decodedToken) {
  if (!decodedToken || !decodedToken.jti) return;
  const expiresAtIso = decodedToken.exp
    ? new Date(decodedToken.exp * 1000).toISOString()
    : new Date(Date.now() + 24 * 3600 * 1000).toISOString();
  tokenBlacklistRepository.blacklist(decodedToken.jti, expiresAtIso);
}

function isTokenBlacklisted(jti) {
  return tokenBlacklistRepository.isBlacklisted(jti);
}

module.exports = {
  hashPassword,
  verifyPassword,
  issueToken,
  login,
  verifyToken,
  logout,
  isTokenBlacklisted,
};
