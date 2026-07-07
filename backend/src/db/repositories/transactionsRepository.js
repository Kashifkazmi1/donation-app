'use strict';

const { getDb } = require('../connection');
const { generateShortId } = require('../../utils/id');

function rowToDto(row) {
  if (!row) return null;
  return {
    transactionId: row.id,
    givewpDonationId: row.givewp_donation_id,
    amount: row.amount,
    currency: row.currency,
    donor: {
      firstName: row.first_name,
      lastName: row.last_name,
      email: row.email,
      phone: row.phone,
      anonymous: Boolean(row.anonymous),
    },
    stripePaymentIntentId: row.payment_intent_id,
    stripeChargeId: row.charge_id,
    status: row.status,
    createdAt: row.created_at,
  };
}

function findByPaymentIntentId(paymentIntentId) {
  const db = getDb();
  const row = db
    .prepare('SELECT * FROM transactions WHERE payment_intent_id = ?')
    .get(paymentIntentId);
  return rowToDto(row);
}

function findByIdempotencyKey(idempotencyKey) {
  const db = getDb();
  const row = db
    .prepare('SELECT * FROM transactions WHERE idempotency_key = ?')
    .get(idempotencyKey);
  return rowToDto(row);
}

/**
 * Creates a transaction row. Idempotent: if a unique-constraint violation
 * fires on `payment_intent_id` or `idempotency_key` (a concurrent retry won
 * the race), the already-existing row is returned instead of raising.
 */
function create({
  paymentIntentId,
  chargeId,
  givewpDonationId,
  amount,
  currency,
  firstName,
  lastName,
  email,
  phone,
  anonymous,
  idempotencyKey,
  status,
}) {
  const db = getDb();
  const id = generateShortId();
  try {
    db.prepare(
      `INSERT INTO transactions
        (id, payment_intent_id, charge_id, givewp_donation_id, amount, currency,
         first_name, last_name, email, phone, anonymous, status, idempotency_key)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      id,
      paymentIntentId,
      chargeId || null,
      givewpDonationId != null ? givewpDonationId : null,
      amount,
      currency,
      firstName || null,
      lastName || null,
      email || null,
      phone || null,
      anonymous ? 1 : 0,
      status || 'completed',
      idempotencyKey
    );
    return findByPaymentIntentId(paymentIntentId);
  } catch (err) {
    if (err && typeof err.code === 'string' && err.code.startsWith('SQLITE_CONSTRAINT')) {
      const existing =
        findByPaymentIntentId(paymentIntentId) || findByIdempotencyKey(idempotencyKey);
      if (existing) return existing;
    }
    throw err;
  }
}

function list({ page = 1, pageSize = 20, status } = {}) {
  const db = getDb();
  const where = status ? 'WHERE status = ?' : '';
  const params = status ? [status] : [];

  const total = db
    .prepare(`SELECT COUNT(*) AS count FROM transactions ${where}`)
    .get(...params).count;

  const offset = (page - 1) * pageSize;
  const rows = db
    .prepare(
      `SELECT * FROM transactions ${where} ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?`
    )
    .all(...params, pageSize, offset);

  return {
    items: rows.map(rowToDto),
    page,
    pageSize,
    total,
  };
}

module.exports = {
  findByPaymentIntentId,
  findByIdempotencyKey,
  create,
  list,
};
