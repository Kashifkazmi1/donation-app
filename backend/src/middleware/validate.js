'use strict';

const { Errors } = require('../utils/errors');

/**
 * Builds an Express middleware that validates `req[source]` (default:
 * request body) against a zod schema, replacing it with the parsed
 * (and thus type-coerced/defaulted) value on success, or forwarding a
 * VALIDATION_ERROR (400) on failure.
 */
function validate(schema, source = 'body') {
  return function validateMiddleware(req, res, next) {
    const result = schema.safeParse(req[source]);
    if (!result.success) {
      const details = result.error.issues.map((issue) => ({
        path: issue.path.join('.'),
        message: issue.message,
      }));
      return next(Errors.validation('Request validation failed', details));
    }
    req[source] = result.data;
    return next();
  };
}

module.exports = { validate };
