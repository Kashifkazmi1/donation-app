-- Donation Terminal backend schema (SQLite).
-- Mirrors docs/API_CONTRACT.md section 3 exactly.
-- All CREATE statements are idempotent so this file is safe to run on
-- every startup / deploy.

CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'staff',
  created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE IF NOT EXISTS token_blacklist (
  jti        TEXT PRIMARY KEY,
  expires_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS donation_intents (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  payment_intent_id TEXT NOT NULL UNIQUE,
  amount            INTEGER NOT NULL,
  currency          TEXT NOT NULL,
  first_name        TEXT,
  last_name         TEXT,
  email             TEXT,
  phone             TEXT,
  anonymous         INTEGER NOT NULL DEFAULT 0,
  idempotency_key   TEXT NOT NULL UNIQUE,
  status            TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'succeeded', 'failed')),
  created_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_donation_intents_status ON donation_intents (status);

CREATE TABLE IF NOT EXISTS transactions (
  id                  TEXT PRIMARY KEY,
  payment_intent_id   TEXT NOT NULL UNIQUE,
  charge_id           TEXT,
  givewp_donation_id  INTEGER,
  amount              INTEGER NOT NULL,
  currency            TEXT NOT NULL,
  first_name          TEXT,
  last_name           TEXT,
  email               TEXT,
  phone               TEXT,
  anonymous           INTEGER NOT NULL DEFAULT 0,
  status              TEXT NOT NULL DEFAULT 'completed',
  idempotency_key     TEXT NOT NULL UNIQUE,
  created_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions (created_at);
