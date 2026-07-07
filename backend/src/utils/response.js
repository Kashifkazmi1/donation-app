'use strict';

function sendSuccess(res, data, statusCode = 200) {
  return res.status(statusCode).json({ success: true, data });
}

function sendError(res, appError) {
  const body = {
    success: false,
    error: {
      code: appError.code,
      message: appError.message,
    },
  };
  if (appError.details !== undefined) {
    body.error.details = appError.details;
  }
  return res.status(appError.statusCode || 500).json(body);
}

module.exports = { sendSuccess, sendError };
