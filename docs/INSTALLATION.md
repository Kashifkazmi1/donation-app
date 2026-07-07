# Installation Guide

End-to-end setup for a fresh deployment: WordPress plugin -> backend -> Android app. Do them in
this order -- each later step needs values produced by the one before it.

## 0. Prerequisites

- A WordPress site with **GiveWP** installed, activated, and at least one donation form
  published.
- A **Stripe** account with **Terminal** enabled, and Stripe already connected to GiveWP (per
  your existing setup).
- A **Stripe Terminal Location** created (Stripe Dashboard -> Terminal -> Locations, or via the
  API) and your **M2 reader(s)** registered to it (Dashboard -> Terminal -> Readers -> pair a
  reader using its registration code, which the M2 displays on its screen).
- A place to run the Node.js backend (a small VM, a container host, or a PaaS -- see
  `DEPLOYMENT.md`) reachable over **HTTPS** from staff phones/tablets.
- Node.js 18+ and npm, for local development/testing of the backend.
- Android Studio (current stable), for building the Android app.

## 1. Install the WordPress bridge plugin

1. Zip the `wordpress-plugin/givewp-stripe-terminal-bridge/` directory (the folder itself, not
   just its contents) into `givewp-stripe-terminal-bridge.zip`.
2. In wp-admin: **Plugins -> Add New -> Upload Plugin**, choose the zip, **Install Now**, then
   **Activate**.
   - If GiveWP isn't active, the plugin shows an admin notice and does not register its REST
     routes until GiveWP is active too.
3. Go to **Settings -> Donation Terminal**:
   - An API key is auto-generated on activation. Click **Reveal**/**Copy** to grab it, or
     **Regenerate** for a fresh one.
   - Optionally choose a **Default Give Form** from the dropdown -- donations created by the
     backend attribute to this form when they don't specify one. If left unset, the earliest
     published form is used.
4. Note the REST base URL shown on that settings page:
   `https://<your-site>/wp-json/donation-terminal/v1`. You'll put this and the API key into the
   backend's `.env` in the next step.
5. Verify it's reachable:
   ```
   curl -H "X-API-Key: <the key from step 3>" \
     https://<your-site>/wp-json/donation-terminal/v1/health
   ```
   Expect `{"success":true,"data":{"givewpActive":true,"version":"1.0.0"}}`.

## 2. Set up Stripe

1. Dashboard -> Developers -> API keys: copy the **Secret key** (`sk_test_...` while testing,
   `sk_live_...` for real donations).
2. Dashboard -> Terminal -> Locations: create/copy a **Location ID** (`tml_...`) and register
   your M2 reader(s) to it.
3. Dashboard -> Developers -> Webhooks: add an endpoint pointing at
   `https://<your-backend>/api/v1/webhooks/stripe`, subscribed to at least:
   `payment_intent.succeeded`, `payment_intent.payment_failed`,
   `terminal.reader.action_succeeded`, `terminal.reader.action_failed`. Copy the **Signing
   secret** (`whsec_...`) it gives you.

## 3. Deploy the backend

1. Copy `backend/.env.example` to `backend/.env` and fill in every value -- `JWT_SECRET`
   (generate with `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`),
   `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_TERMINAL_LOCATION_ID`,
   `GIVEWP_API_BASE_URL`, `GIVEWP_API_KEY` from steps 1-2 above.
2. From `backend/`:
   ```
   npm install
   npm run migrate        # creates the SQLite file + tables
   npm run create-user -- --username=staff1 --password="a-strong-password" --name="Staff One" --role=staff
   npm test                # optional, but a good sanity check
   npm start                # or run it under a process manager -- see DEPLOYMENT.md
   ```
3. Confirm it's up: `curl https://<your-backend>/api/v1/health` should report
   `stripe.configured: true` and `givewp.givewpActive: true`.
4. Import `postman/Donation-Terminal.postman_collection.json` into Postman, set `baseUrl` to
   your backend's URL + `/api/v1`, run **Login** with the staff account you created, and try a
   couple of other requests to confirm end-to-end wiring before touching the Android app.

Create one staff account per person who'll use the app (there's no self-signup by design -- this
app is for staff/volunteers only).

## 4. Build and install the Android app

1. Open `android/` in Android Studio and let Gradle sync (needs network access to `google()` and
   `mavenCentral()`).
2. Build a debug APK (`./gradlew assembleDebug`) or a signed release build (see `DEPLOYMENT.md`
   for release signing) and install it on the staff device(s).
3. On first launch: **Login** with a staff account, then go to **Dashboard -> Settings** and set:
   - **API Base URL**: `https://<your-backend>/api/v1/`
   - **Terminal Location ID**: the `tml_...` from step 2.
   - **Currency**: your default donation currency.
   - **Test/Live Mode**: matches whichever Stripe key you put in the backend's `.env`.
4. Go to **Reader Connection**, enable Bluetooth if prompted, grant the requested
   Bluetooth/location permissions, and confirm it discovers and connects to the M2 reader.
5. Do one full end-to-end test donation with a real or Stripe test card, confirm it appears in
   **Transaction History** in the app and as a completed donation in wp-admin's GiveWP donations
   list with gateway "Stripe Terminal (In Person)" and the correct transaction ID.

## Troubleshooting

- **Backend health check shows `givewp.givewpActive: false` or a connection error** -- re-check
  `GIVEWP_API_BASE_URL`/`GIVEWP_API_KEY` in `backend/.env` and that the WordPress site is
  reachable from wherever the backend runs (firewall/security-plugin rules can block server-to-
  server requests even when the site is public in a browser).
- **App can't reach the backend** -- check the API Base URL in Settings includes `/api/v1/`, and
  that the backend is served over HTTPS reachable from the device's network (not just
  localhost).
- **Reader won't connect** -- confirm the M2 is charged/powered on, within Bluetooth range,
  registered to the same Stripe Terminal Location ID configured in the app's Settings, and that
  Bluetooth + location permissions were granted (Stripe's SDK requires location permission for
  BLE reader discovery on every Android version).
- **Donation succeeds on the reader but doesn't appear in GiveWP** -- check the backend logs
  around `/donations/complete`; the Stripe webhook (`payment_intent.succeeded`) also acts as a
  backstop that retries the GiveWP creation if the app's direct call failed, so it usually
  recovers within a minute even after a transient error.
