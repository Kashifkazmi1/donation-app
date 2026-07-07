'use strict';

const { getDb } = require('../connection');

function blacklist(jti, expiresAtIso) {
  const db = getDb();
  db.prepare(
    'INSERT OR REPLACE INTO token_blacklist (jti, expires_at) VALUES (?, ?)'
  ).run(jti, expiresAtIso);
}

function isBlacklisted(jti) {
  const db = getDb();
  const row = db.prepare('SELECT jti FROM token_blacklist WHERE jti = ?').get(jti);
  return Boolean(row);
}

/** Housekeeping: drop blacklist entries for tokens that have already expired
 * naturally (they'd be rejected by JWT expiry checks anyway). */
function purgeExpired() {
  const db = getDb();
  db.prepare('DELETE FROM token_blacklist WHERE expires_at <= ?').run(
    new Date().toISOString()
  );
}

module.exports = { blacklist, isBlacklisted, purgeExpired };
