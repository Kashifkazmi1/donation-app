# API Contract — Donation Terminal (Android + Backend + WordPress/GiveWP)

This is the single source of truth for how the Android app, the Node.js backend, and the
WordPress/GiveWP bridge plugin talk to each other. All three codebases must conform to this
document exactly. If a change is needed, update this file first.

Repo layout:
```
backend/               Node.js/Express API (talks to Stripe + WordPress)
android/                Android Studio project (Kotlin, staff-facing app)
wordpress-plugin/       GiveWP bridge plugin (installed on the WP site)
docs/                   Documentation
postman/                Postman collection
```

## 1. Conventions

- All backend endpoints are prefixed `/api/v1`.
- All request/response bodies are JSON, `Content-Type: application/json`, except the raw
  Stripe webhook body.
- Money amounts sent to Stripe are integers in the smallest currency unit (cents). Money
  amounts sent to the WordPress bridge are decimal major-unit strings (e.g. `"25.00"`), because
  GiveWP stores donations in major units.
- Success envelope: `{ "success": true, "data": { ... } }`
- Error envelope: `{ "success": false, "error": { "code": "STRING_CODE", "message": "human readable", "details": {...optional} } }`
- All authenticated backend endpoints require `Authorization: Bearer <JWT>`.
- Idempotency: every donation-creating call carries a client-generated `idempotencyKey`
  (UUID v4). The backend enforces a unique constraint on it. The WordPress bridge additionally
  de-dupes on `stripePaymentIntentId`.
- Timestamps are ISO-8601 UTC strings.

## 2. Backend REST API (Node/Express)

### POST /api/v1/auth/login
Request:
```json
{ "username": "staff1", "password": "secret" }
```
Response 200:
```json
{ "success": true, "data": {
  "token": "<jwt>",
  "expiresIn": 43200,
  "user": { "id": "1", "username": "staff1", "name": "Staff One", "role": "staff" }
}}
```
Errors: `INVALID_CREDENTIALS` (401), `VALIDATION_ERROR` (400).

### POST /api/v1/auth/logout
Auth required. Adds the token's `jti` to a blacklist so it can no longer be used before it
expires. Response 200: `{ "success": true, "data": { "loggedOut": true } }`

### POST /api/v1/terminal/connection-token
Auth required. Backend calls `stripe.terminal.connectionTokens.create()` and returns the
secret — required by the Stripe Terminal SDK's `ConnectionTokenProvider`. Never call Stripe
directly from the app.
Response 200: `{ "success": true, "data": { "secret": "pst_..." } }`

### GET /api/v1/terminal/reader-status
Auth required. Returns Stripe-registered readers for the configured location (informational —
actual BLE connection happens on-device via the Terminal SDK).
Response 200:
```json
{ "success": true, "data": {
  "location": { "id": "tml_...", "displayName": "Main Office" },
  "readers": [
    { "id": "tmr_...", "label": "Counter M2", "serialNumber": "STRM26...", "deviceType": "stripe_m2",
      "status": "online", "batteryLevel": 0.86, "locationId": "tml_..." }
  ]
}}
```

### POST /api/v1/payments/intent
Auth required. Creates a Stripe PaymentIntent for `card_present` capture via Terminal, and
stores a local `donation_intents` row (status `pending`) holding the donor info so the app
doesn't have to resend it later.
Request:
```json
{
  "amount": 2500,
  "currency": "usd",
  "anonymous": false,
  "donor": { "firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "phone": "+15551234567" },
  "idempotencyKey": "b3b3b3b3-...-uuid"
}
```
`amount` is required, integer, > 0 (smallest currency unit). `donor` fields are all optional.
Response 200:
```json
{ "success": true, "data": {
  "paymentIntentId": "pi_...",
  "clientSecret": "pi_..._secret_...",
  "amount": 2500,
  "currency": "usd"
}}
```
Errors: `VALIDATION_ERROR` (400), `STRIPE_ERROR` (502).

### POST /api/v1/donations/complete
Auth required. Called by the app immediately after the Terminal SDK reports the PaymentIntent
as `succeeded`. The backend:
1. Re-fetches the PaymentIntent from Stripe and verifies `status === "succeeded"`.
2. Loads the stored `donation_intents` row by `paymentIntentId` for donor info/amount.
3. If a `transactions` row already exists for this `paymentIntentId`, returns it unchanged
   (idempotent — safe to retry).
4. Otherwise calls the WordPress bridge (`POST /donations`) to create the GiveWP donation,
   stores a `transactions` row, and returns it.
Request:
```json
{ "paymentIntentId": "pi_...", "idempotencyKey": "b3b3b3b3-...-uuid" }
```
Response 200:
```json
{ "success": true, "data": {
  "transactionId": "1a2b3c",
  "givewpDonationId": 4821,
  "amount": 2500,
  "currency": "usd",
  "donor": { "firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "phone": "+15551234567", "anonymous": false },
  "stripePaymentIntentId": "pi_...",
  "stripeChargeId": "ch_...",
  "status": "completed",
  "createdAt": "2026-07-07T12:00:00.000Z"
}}
```
Errors: `PAYMENT_NOT_SUCCEEDED` (409), `INTENT_NOT_FOUND` (404), `GIVEWP_ERROR` (502).

### GET /api/v1/transactions?page=1&pageSize=20&status=completed
Auth required. Lists local transaction history (most recent first).
Response 200:
```json
{ "success": true, "data": {
  "items": [ { "...same shape as complete-donation response item..." } ],
  "page": 1, "pageSize": 20, "total": 137
}}
```

### GET /api/v1/health
No auth. Liveness probe for backend, Stripe key presence, and WordPress bridge reachability.

### POST /api/v1/webhooks/stripe
No JWT — verified via `Stripe-Signature` header + `STRIPE_WEBHOOK_SECRET`, raw body. Handles:
- `payment_intent.succeeded` — reconciliation backstop: if no `transactions` row exists yet for
  this PaymentIntent (app may have crashed before calling `/donations/complete`), create the
  GiveWP donation now, using the same idempotent code path.
- `payment_intent.payment_failed` — marks the local `donation_intents` row `failed`.
- `terminal.reader.action_succeeded` / `terminal.reader.action_failed` — logged for audit.

All webhook handlers respond `200` quickly; failures are logged, not retried synchronously.

## 3. Local database (SQLite, file-based — see backend/src/db/schema.sql)

- `users` — staff accounts: `id, username, password_hash, name, role, created_at`
- `token_blacklist` — `jti, expires_at` (for logout)
- `donation_intents` — `payment_intent_id (unique), amount, currency, first_name, last_name, email, phone, anonymous, idempotency_key (unique), status(pending|succeeded|failed), created_at`
- `transactions` — `id, payment_intent_id (unique), charge_id, givewp_donation_id, amount, currency, first_name, last_name, email, phone, anonymous, status, idempotency_key (unique), created_at`

## 4. WordPress/GiveWP bridge plugin REST API

Installed on the WordPress site, namespace `donation-terminal/v1`. Called only by the backend
server (never by the Android app directly), authenticated with a shared secret header:
`X-API-Key: <GIVEWP_API_KEY>` matching a value stored in `wp_options`.

### GET /wp-json/donation-terminal/v1/health
Returns `{ "success": true, "data": { "givewpActive": true, "version": "1.0.0" } }`.

### POST /wp-json/donation-terminal/v1/donations
Request:
```json
{
  "amount": "25.00",
  "currency": "USD",
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phone": "+15551234567",
  "anonymous": false,
  "gateway": "stripe_terminal",
  "status": "publish",
  "stripePaymentIntentId": "pi_...",
  "stripeChargeId": "ch_...",
  "stripeTransactionId": "ch_...",
  "formId": null,
  "date": "2026-07-07T12:00:00.000Z"
}
```
- `formId` optional; falls back to the plugin's configured default Give Form ID (or the
  earliest published form if none configured).
- Idempotent on `stripePaymentIntentId`: if a donation with that meta value already exists,
  returns the existing donation instead of creating a duplicate.
Response 200/201:
```json
{ "success": true, "data": { "donationId": 4821, "donorId": 312, "status": "publish" } }
```
Errors: `400` invalid payload, `401` bad/missing API key, `500` GiveWP not active / insert failed.

Donation is created with:
- Gateway ID `stripe_terminal` (registered as a GiveWP gateway label by the plugin so it shows
  correctly in the GiveWP admin UI, e.g. "Stripe Terminal (In Person)").
- Payment status `publish` (GiveWP's "Complete").
- Donor meta / payment meta stores `_give_stripe_terminal_payment_intent_id`,
  `_give_stripe_terminal_charge_id`, and the payment's transaction ID set via
  `give_set_payment_transaction_id()` so it displays exactly like a normal Stripe donation.
- Anonymous donations set GiveWP's standard anonymous flag/meta.

## 5. Environment variables

Backend (`backend/.env`): `PORT, NODE_ENV, JWT_SECRET, JWT_EXPIRES_IN, DATABASE_PATH,
STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, STRIPE_TERMINAL_LOCATION_ID, GIVEWP_API_BASE_URL,
GIVEWP_API_KEY, GIVEWP_DEFAULT_FORM_ID, DEFAULT_CURRENCY, CORS_ALLOWED_ORIGINS, LOG_LEVEL,
RATE_LIMIT_WINDOW_MS, RATE_LIMIT_MAX`.

WordPress: configured via the plugin's admin settings page (Settings → Donation Terminal),
stored as `wp_options`: API key, default Give Form ID.

Android (configured on-device via Settings screen, persisted in DataStore — not secrets):
API Base URL, Terminal Location ID, Currency, Test/Live mode. The JWT is stored in
EncryptedSharedPreferences. The Stripe **secret** key never appears in the app; the app only
ever calls the backend's `/terminal/connection-token` endpoint.
