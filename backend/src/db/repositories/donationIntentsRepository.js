'use strict';

const { getDb } = require('../connection');

function rowToDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    paymentIntentId: row.payment_intent_id,
    amount: row.amount,
    currency: row.currency,
    firstName: row.first_name,
    lastName: row.last_name,
    email: row.email,
    phone: row.phone,
    anonymous: Boolean(row.anonymous),
    idempotencyKey: row.idempotency_key,
    status: row.status,
    createdAt: row.created_at,
  };
}

function create({
  paymentIntentId,
  amount,
  currency,
  firstName,
  lastName,
  email,
  phone,
  anonymous,
  idempotencyKey,
}) {
  const db = getDb();
  db.prepare(
    `INSERT INTO donation_intents
      (payment_intent_id, amount, currency, first_name, last_name, email, phone, anonymous, idempotency_key, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')`
  ).run(
    paymentIntentId,
    amount,
    currency,
    firstName || null,
    lastName || null,
    email || null,
    phone || null,
    anonymous ? 1 : 0,
    idempotencyKey
  );
  return findByPaymentIntentId(paymentIntentId);
}

function findByPaymentIntentId(paymentIntentId) {
  const db = getDb();
  const row = db
    .prepare('SELECT * FROM donation_intents WHERE payment_intent_id = ?')
    .get(paymentIntentId);
  return rowToDto(row);
}

function findByIdempotencyKey(idempotencyKey) {
  const db = getDb();
  const row = db
    .prepare('SELECT * FROM donation_intents WHERE idempotency_key = ?')
    .get(idempotencyKey);
  return rowToDto(row);
}

function updateStatus(paymentIntentId, status) {
  const db = getDb();
  db.prepare('UPDATE donation_intents SET status = ? WHERE payment_intent_id = ?').run(
    status,
    paymentIntentId
  );
  return findByPaymentIntentId(paymentIntentId);
}

module.exports = {
  create,
  findByPaymentIntentId,
  findByIdempotencyKey,
  updateStatus,
};
