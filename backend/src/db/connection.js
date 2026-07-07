'use strict';

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const config = require('../config/env');
const logger = require('../utils/logger');
const { applySchema } = require('./migrate');

let db;

function getDb() {
  if (db) return db;

  const dbPath = config.database.path;
  if (dbPath !== ':memory:') {
    const dir = path.dirname(dbPath);
    fs.mkdirSync(dir, { recursive: true });
  }

  db = new Database(dbPath);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  applySchema(db);
  logger.info({ dbPath }, 'SQLite database ready');

  return db;
}

/** Test-only helper to force a fresh in-memory database on next getDb(). */
function resetDb() {
  if (db) {
    db.close();
    db = undefined;
  }
}

module.exports = { getDb, resetDb };
