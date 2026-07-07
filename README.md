# Donation Terminal

An in-person card donation system for nonprofit staff/volunteers: an Android app drives a
**Stripe Terminal M2** Bluetooth card reader, a **Node.js backend** brokers the payment with
Stripe, and a **WordPress/GiveWP bridge plugin** records every successful payment as a normal,
completed GiveWP donation on your existing site.

```
android/               Native Android app (Kotlin, MVVM, Compose/Material 3, Stripe Terminal SDK)
backend/                Node.js/Express API (JWT auth, Stripe Terminal + PaymentIntents, GiveWP client)
wordpress-plugin/       WordPress plugin: REST bridge that creates completed GiveWP donations
docs/                   API contract, installation guide, deployment guide
postman/                Postman collection for exercising the backend API
```

## How it fits together

```
 Staff device                Backend (Node/Express)              WordPress + GiveWP
┌─────────────────┐  HTTPS  ┌────────────────────────┐  HTTPS   ┌───────────────────────┐
│ Android app      │────────▶│ JWT auth                │─────────▶│ Bridge plugin REST     │
│ + Stripe Terminal│         │ Stripe Terminal/Payment │         │ endpoint (API-key auth)│
│   SDK ↔ M2 reader│◀─BLE────│  Intents                │◀────────│ → give_insert_payment() │
│                  │  Stripe │ SQLite (staff, tx log)  │         │   with Stripe meta      │
└─────────────────┘  API     └────────────────────────┘         └───────────────────────┘
```

1. Staff enters a donation amount (and optional donor info) in the app.
2. The app connects to the M2 reader over Bluetooth via the Stripe Terminal SDK.
3. The backend creates a Stripe PaymentIntent; the app collects the card (tap/insert/swipe) and
   confirms it via the Terminal SDK.
4. On success, the app calls the backend, which creates a **completed** donation in GiveWP with
   the Stripe payment intent/charge/transaction IDs attached -- it shows up exactly like a normal
   GiveWP donation, gateway-labeled "Stripe Terminal (In Person)".
5. A Stripe webhook to the backend acts as a reconciliation backstop in case step 4 is
   interrupted (app crash, connectivity loss right after a successful card charge).

The full request/response contract between all three pieces is in
**[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md)** -- read that first if you're modifying any of
the three codebases, since they're built against it as a single source of truth.

## Getting started

New deployment? Follow **[`docs/INSTALLATION.md`](docs/INSTALLATION.md)** in order (WordPress
plugin → Stripe → backend → Android app). Going to production? See
**[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)** for hosting, HTTPS, release signing, and a rollout
checklist.

Quick local backend smoke test:
```bash
cd backend
cp .env.example .env   # fill in real values, see comments in the file
npm install
npm run migrate
npm run create-user -- --username=staff1 --password="a-strong-password" --name="Staff One" --role=staff
npm test
npm run dev
```
Then import **[`postman/Donation-Terminal.postman_collection.json`](postman/Donation-Terminal.postman_collection.json)**
into Postman to exercise the API without the Android app.

## Tech stack

- **Android**: Kotlin, MVVM + Clean Architecture, Jetpack Compose + Material 3, Hilt, Retrofit +
  Moshi, Room (offline transaction cache), DataStore + EncryptedSharedPreferences, Navigation
  Compose, Stripe Terminal Android SDK. See [`android/README.md`](android/README.md).
- **Backend**: Node.js, Express, JWT + bcrypt auth, `better-sqlite3`, the official `stripe`
  package, zod validation, pino logging, Jest/Supertest tests. See
  [`backend/README.md`](backend/README.md).
- **WordPress**: a small standalone plugin (`wordpress-plugin/givewp-stripe-terminal-bridge/`)
  using GiveWP's own donation/donor APIs -- no core files modified.

## Security notes

- The Stripe **secret** key never leaves the backend; the app only ever calls the backend's
  `/terminal/connection-token` endpoint, never Stripe directly for anything requiring the secret
  key.
- All backend endpoints require a JWT (`Authorization: Bearer ...`) except `/health` and the
  Stripe webhook (which is verified via `Stripe-Signature` instead).
- The WordPress bridge endpoint is authenticated with a shared `X-API-Key`, intended to be called
  only by the backend server -- never expose it to the Android app or a browser.
- Every donation-creating request carries a client-generated idempotency key; retries (network
  blips, app restarts) are safe and never create duplicate donations, enforced at both the
  backend (unique DB constraints) and the WordPress bridge (de-duped by Stripe payment intent
  ID).
- This app is intentionally staff-only -- there's no public signup; provision one account per
  staff member with the backend's `create-user` script.

## Repo-specific docs

| File | What's in it |
|---|---|
| [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) | Full API reference: every endpoint, request/response shape, error codes, DB schema, env vars. |
| [`docs/INSTALLATION.md`](docs/INSTALLATION.md) | Step-by-step first-time setup. |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Production hosting, HTTPS, release signing, rollout checklist. |
| [`backend/README.md`](backend/README.md) | Backend-internal dev notes (running/testing locally). |
| [`android/README.md`](android/README.md) | Opening the project, pointing it at a backend, architecture notes. |
| [`wordpress-plugin/givewp-stripe-terminal-bridge/readme.txt`](wordpress-plugin/givewp-stripe-terminal-bridge/readme.txt) | Standard WP plugin readme. |
