'use strict';

const { AppError, Errors } = require('../utils/errors');
const { sendError } = require('../utils/response');
const logger = require('../utils/logger');

function notFoundHandler(req, res) {
  sendError(res, new AppError('NOT_FOUND', `No route: ${req.method} ${req.originalUrl}`, 404));
}

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  if (err instanceof AppError) {
    if (err.statusCode >= 500) {
      logger.error({ err: err.message, code: err.code }, 'Request failed');
    } else {
      logger.warn({ err: err.message, code: err.code }, 'Request rejected');
    }
    return sendError(res, err);
  }

  if (err && err.type === 'entity.parse.failed') {
    return sendError(res, Errors.validation('Malformed JSON body'));
  }

  logger.error({ err: err && err.stack ? err.stack : err }, 'Unhandled error');
  return sendError(res, Errors.internal());
}

module.exports = { errorHandler, notFoundHandler };
