'use strict';

const { getDb } = require('../connection');

function findByUsername(username) {
  const db = getDb();
  return db.prepare('SELECT * FROM users WHERE username = ?').get(username);
}

function findById(id) {
  const db = getDb();
  return db.prepare('SELECT * FROM users WHERE id = ?').get(id);
}

function create({ username, passwordHash, name, role }) {
  const db = getDb();
  const result = db
    .prepare(
      'INSERT INTO users (username, password_hash, name, role) VALUES (?, ?, ?, ?)'
    )
    .run(username, passwordHash, name, role || 'staff');
  return findById(result.lastInsertRowid);
}

module.exports = { findByUsername, findById, create };
