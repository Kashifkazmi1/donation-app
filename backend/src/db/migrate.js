'use strict';

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const SCHEMA_PATH = path.join(__dirname, 'schema.sql');

/** Executes schema.sql against an already-open database handle. Idempotent. */
function applySchema(db) {
  const sql = fs.readFileSync(SCHEMA_PATH, 'utf8');
  db.exec(sql);
  return db;
}

/** Standalone migration entry point: opens (creating if needed) the DB file
 * at DATABASE_PATH and applies the schema. Used by `npm run migrate` and by
 * the server on startup, so the DB is always ready on first run. */
function migrate(dbPath) {
  if (dbPath !== ':memory:') {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  }
  const db = new Database(dbPath);
  applySchema(db);
  return db;
}

module.exports = { applySchema, migrate };

if (require.main === module) {
  // eslint-disable-next-line global-require
  const config = require('../config/env');
  const db = migrate(config.database.path);
  // eslint-disable-next-line no-console
  console.log(`Migration complete. Database at: ${config.database.path}`);
  db.close();
}
