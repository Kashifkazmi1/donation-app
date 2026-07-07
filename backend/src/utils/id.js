'use strict';

const crypto = require('crypto');

/**
 * Generates a short hex id for locally-created records (e.g. transactions),
 * matching the style shown in the API contract ("transactionId": "1a2b3c").
 */
function generateShortId(bytes = 6) {
  return crypto.randomBytes(bytes).toString('hex');
}

module.exports = { generateShortId };
