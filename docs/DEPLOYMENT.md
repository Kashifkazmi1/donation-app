# Production Deployment

## Backend (Node.js/Express)

### Runtime requirements

- Node.js 18+, a persistent filesystem for the SQLite file (`DATABASE_PATH`), and outbound HTTPS
  access to `api.stripe.com` and your WordPress site.
- **HTTPS is mandatory** in production -- the app sends the staff JWT and payment data over this
  API. Terminate TLS at a reverse proxy (nginx, Caddy, your PaaS's load balancer) in front of the
  Node process; the app itself only speaks plain HTTP.
- SQLite (via `better-sqlite3`) is a single file on disk. It's appropriate for one backend
  instance serving a nonprofit's staff/volunteer donation traffic. If you outgrow a single
  instance (need to run more than one backend process/container for the same deployment, e.g.
  for zero-downtime deploys or horizontal scaling), migrate `src/db/connection.js` and the
  repository modules in `src/db/repositories/` to a networked database (Postgres/MySQL) first --
  SQLite's file-based locking does not support multiple concurrent writer processes safely.

### Recommended process layout

Run the backend under a supervisor that restarts it on crash and captures logs, for example:

**systemd unit** (`/etc/systemd/system/donation-terminal-backend.service`):
```ini
[Unit]
Description=Donation Terminal backend
After=network.target

[Service]
Type=simple
User=donation-terminal
WorkingDirectory=/opt/donation-terminal/backend
EnvironmentFile=/opt/donation-terminal/backend/.env
ExecStart=/usr/bin/node src/index.js
Restart=on-failure
RestartSec=5
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```
```
sudo systemctl daemon-reload
sudo systemctl enable --now donation-terminal-backend
```

**Or containerized** -- a minimal production `Dockerfile` (add this file if you prefer
containers over systemd; not included by default since deployment targets vary):
```dockerfile
FROM node:18-slim
WORKDIR /app
COPY backend/package*.json ./
RUN npm ci --omit=dev
COPY backend/ .
RUN mkdir -p data
ENV NODE_ENV=production
EXPOSE 3000
CMD ["node", "src/index.js"]
```
Mount a volume at `/app/data` so the SQLite file survives container restarts/redeploys, and pass
env vars via your platform's secrets mechanism -- never bake `.env` into the image.

### Reverse proxy (nginx example)

```nginx
server {
    listen 443 ssl;
    server_name backend.your-org.example;

    ssl_certificate     /etc/letsencrypt/live/backend.your-org.example/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/backend.your-org.example/privkey.pem;

    location /api/v1/webhooks/stripe {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        # Do not buffer/alter the body here -- Stripe signature verification needs the exact
        # raw bytes the backend receives.
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Secrets and configuration

- Never commit `backend/.env`. Provision `JWT_SECRET`, `STRIPE_SECRET_KEY`,
  `STRIPE_WEBHOOK_SECRET`, and `GIVEWP_API_KEY` via your platform's secret store (systemd
  `EnvironmentFile` with restricted permissions, your PaaS's secret manager, etc.).
- Use `STRIPE_SECRET_KEY`/`sk_live_...` and a **live-mode** webhook signing secret only once
  you've tested end-to-end in test mode. The Android app's Settings screen has a Test/Live Mode
  toggle purely as a visual reminder for staff -- the mode is actually determined by which
  Stripe key the *backend* holds.
- Back up the SQLite file (`DATABASE_PATH`) regularly -- it's the local transaction history and
  staff account store. GiveWP on WordPress remains the durable source of truth for the donations
  themselves either way.
- Rotate `GIVEWP_API_KEY` periodically via the plugin's **Regenerate API Key** button, updating
  the backend's `.env` (and restarting the backend) at the same time.

### Health/monitoring

`GET /api/v1/health` (no auth) checks Stripe key presence and reachability of the WordPress
bridge -- point your uptime monitor at it. Logs are structured JSON (pino); ship them to your
usual log aggregator. `LOG_LEVEL` defaults to `info`.

## WordPress plugin

No special deployment steps beyond a normal plugin install (see `INSTALLATION.md`) -- it runs
inside your existing WordPress hosting. Make sure whatever security plugin/WAF you run allows
POST requests to `/wp-json/donation-terminal/v1/donations` from your backend's IP/egress range,
and that the `X-API-Key` header isn't stripped by a caching layer in front of WordPress (the
REST API generally bypasses page caches, but confirm with your host if donations aren't landing).

## Android app

### Release signing

1. Generate a release keystore (keep it outside version control, back it up securely -- losing
   it means you can never update the app under the same package name/signature again):
   ```
   keytool -genkey -v -keystore donation-terminal-release.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias donation-terminal
   ```
2. Configure signing in `android/app/build.gradle.kts` (a `signingConfigs { release { ... } }`
   block reading from `local.properties`/environment variables -- do not hardcode the keystore
   password in a committed file) or sign via `apksigner` as a separate CI step.
3. Build: `./gradlew bundleRelease` (for a Play Console upload) or `./gradlew assembleRelease`
   (for direct APK distribution to managed devices).

### Distribution

Since this app is for staff/volunteers only (not the public), prefer one of:
- **Private Google Play track** (internal testing or a closed track restricted to your org's
  Google Workspace group) -- gives you update management for free.
- **Managed Google Play** if your devices are enrolled in an MDM (Android Enterprise) --
  push installs/updates centrally, no Play Store account needed per device.
- **Direct APK install** for a small number of devices you control physically -- acceptable for
  a handful of tablets/phones but you own update distribution yourself.

Either way, devices need: Bluetooth LE hardware, Android 8.0+ (API 26, per `minSdk`), and network
access to your backend's HTTPS URL.

### Per-deployment configuration

Nothing about the backend URL, Terminal Location ID, or currency is hardcoded at build time --
staff configure it once on first login via **Settings**, and it persists in the app's local
storage (DataStore). This means the *same* release build can point at a test backend during
setup/rehearsal and a production backend on event day, just by changing Settings -- no rebuild
needed. If you manage many devices via MDM, you can alternatively pre-seed these via your MDM's
app-config mechanism if you extend the app to read them (not implemented by default -- the
Settings screen is the supported path).

## Rollout checklist

- [ ] WordPress plugin installed/activated, API key generated, default form chosen.
- [ ] Stripe Terminal Location created, M2 reader(s) registered to it.
- [ ] Stripe webhook endpoint added and pointed at the backend's `/api/v1/webhooks/stripe`.
- [ ] Backend deployed behind HTTPS, `.env` fully populated, `npm run migrate` run once.
- [ ] At least one staff account created (`npm run create-user`).
- [ ] `GET /api/v1/health` returns healthy.
- [ ] Android release build signed and installed on staff devices, Settings configured.
- [ ] One full end-to-end **test-mode** donation completed and confirmed in GiveWP.
- [ ] Switched Stripe keys/webhook secret to **live mode** and repeated the end-to-end test with
      a small real donation before relying on it for an event.
