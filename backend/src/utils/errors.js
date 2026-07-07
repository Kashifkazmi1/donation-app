'use strict';

/**
 * Application error carrying an HTTP status code and a machine-readable
 * error code, matching the API contract's error envelope:
 *   { success: false, error: { code, message, details? } }
 */
class AppError extends Error {
  constructor(code, message, statusCode = 500, details = undefined) {
    super(message);
    this.name = 'AppError';
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
  }
}

const Errors = {
  validation: (message, details) => new AppError('VALIDATION_ERROR', message, 400, details),
  invalidCredentials: (message = 'Invalid username or password') =>
    new AppError('INVALID_CREDENTIALS', message, 401),
  unauthorized: (message = 'Authentication required') => new AppError('UNAUTHORIZED', message, 401),
  forbidden: (message = 'Not allowed') => new AppError('FORBIDDEN', message, 403),
  notFound: (code, message) => new AppError(code, message, 404),
  intentNotFound: (message = 'Donation intent not found') =>
    new AppError('INTENT_NOT_FOUND', message, 404),
  paymentNotSucceeded: (message = 'Payment has not succeeded yet') =>
    new AppError('PAYMENT_NOT_SUCCEEDED', message, 409),
  stripeError: (message = 'Stripe request failed', details) =>
    new AppError('STRIPE_ERROR', message, 502, details),
  givewpError: (message = 'GiveWP bridge request failed', details) =>
    new AppError('GIVEWP_ERROR', message, 502, details),
  internal: (message = 'Internal server error', details) =>
    new AppError('INTERNAL_ERROR', message, 500, details),
};

module.exports = { AppError, Errors };
