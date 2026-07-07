'use strict';

const jwt = require('jsonwebtoken');
const authService = require('../services/authService');
const { Errors } = require('../utils/errors');

function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');

  if (scheme !== 'Bearer' || !token) {
    return next(Errors.unauthorized('Missing or malformed Authorization header'));
  }

  let decoded;
  try {
    decoded = authService.verifyToken(token);
  } catch (err) {
    if (err instanceof jwt.TokenExpiredError) {
      return next(Errors.unauthorized('Token has expired'));
    }
    return next(Errors.unauthorized('Invalid token'));
  }

  if (decoded.jti && authService.isTokenBlacklisted(decoded.jti)) {
    return next(Errors.unauthorized('Token has been revoked'));
  }

  req.user = {
    id: decoded.sub,
    username: decoded.username,
    name: decoded.name,
    role: decoded.role,
  };
  req.tokenPayload = decoded;
  req.rawToken = token;

  return next();
}

module.exports = { requireAuth };
