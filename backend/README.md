# Donation Terminal — Backend

Node.js/Express API implementing `docs/API_CONTRACT.md`. Talks to Stripe (Terminal +
PaymentIntents) and to the WordPress/GiveWP bridge plugin.

This file is for backend-internal dev notes only. See the repo-root docs for the full API
contract, environment variable reference, and Postman collection.

## Running locally

```bash
npm install
npm run migrate      # creates the SQLite file + tables if they don't exist yet
npm run create-user -- --username=staff1 --password=changeme123 --name="Staff One" --role=staff
npm run dev           # nodemon, or `npm start` for a plain run
```

Configure via `backend/.env` (see `docs/API_CONTRACT.md` section 5 for the full variable list:
`PORT, NODE_ENV, JWT_SECRET, JWT_EXPIRES_IN, DATABASE_PATH, STRIPE_SECRET_KEY,
STRIPE_WEBHOOK_SECRET, STRIPE_TERMINAL_LOCATION_ID, GIVEWP_API_BASE_URL, GIVEWP_API_KEY,
GIVEWP_DEFAULT_FORM_ID, DEFAULT_CURRENCY, CORS_ALLOWED_ORIGINS, LOG_LEVEL,
RATE_LIMIT_WINDOW_MS, RATE_LIMIT_MAX`).

The server assumes TLS termination happens upstream (reverse proxy / load balancer) — it does
not terminate HTTPS itself.

## Tests

```bash
npm test
```

Tests run against an in-memory SQLite DB (`DATABASE_PATH=:memory:`, set in `tests/setup.js`)
and mock both the `stripe` package (`tests/mocks/stripeMock.js`) and the GiveWP HTTP client
(`src/services/givewpClient.js`, via `jest.mock`) — no real network calls are made.

## Layout

- `src/app.js` — Express app factory (helmet/cors/logging/routes/error handling).
- `src/index.js` — process entry point (starts the HTTP server).
- `src/db/` — `schema.sql`, connection/migration, and repositories (one per table).
- `src/services/` — Stripe client, GiveWP HTTP client, auth, and the shared idempotent
  `donationService.completeDonation()` used by both `/donations/complete` and the
  `payment_intent.succeeded` webhook handler.
- `src/routes/` — one file per resource, matching the contract's endpoint groupings.
- `src/scripts/createUser.js` — CLI for provisioning staff accounts (`npm run create-user`).
