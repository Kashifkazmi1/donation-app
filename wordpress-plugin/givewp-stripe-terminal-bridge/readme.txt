=== GiveWP Stripe Terminal Bridge ===
Contributors: donationterminal
Tags: givewp, stripe, stripe terminal, donations, rest-api
Requires at least: 5.6
Tested up to: 6.6
Requires PHP: 7.2
Stable tag: 1.0.0
License: GPLv2 or later
License URI: https://www.gnu.org/licenses/gpl-2.0.html

REST bridge that lets the Donation Terminal Node.js backend create completed GiveWP donations after an in-person Stripe Terminal card payment succeeds.

== Description ==

This plugin is one part of a three-piece "Donation Terminal" system used by nonprofit staff to
take in-person donations with an Android app and a Stripe Terminal M2 card reader:

1. The Android app collects a card payment via the Stripe Terminal SDK.
2. Once Stripe confirms the payment succeeded, a Node.js backend calls this plugin's REST API.
3. This plugin creates a fully "Complete" GiveWP donation record, so it appears in GiveWP's
   reports, receipts, and admin donation list exactly like any other donation.

This plugin does **not** implement a GiveWP checkout gateway and does not process any card
payments itself -- payment already happened externally via the Stripe Terminal SDK before this
plugin is ever called. It only records the result in GiveWP and registers a friendly
"Stripe Terminal (In Person)" label so the donation doesn't show up as an "Unknown Gateway" in
wp-admin.

= REST API =

Namespace `donation-terminal/v1`, authenticated with a shared `X-API-Key` header (set on
Settings > Donation Terminal):

* `GET /wp-json/donation-terminal/v1/health`
* `POST /wp-json/donation-terminal/v1/donations`

See the project's `docs/API_CONTRACT.md` (in the main donation-app repository, not shipped
inside this plugin) for the exact request/response shape.

= Requirements =

* GiveWP must be installed and active. This plugin registers no REST routes and shows an
  admin notice instead if GiveWP is missing/inactive.

== Installation ==

1. Make sure GiveWP is installed and active.
2. Upload the `givewp-stripe-terminal-bridge` folder to `/wp-content/plugins/`, or install the
   zipped plugin via Plugins > Add New > Upload Plugin.
3. Activate the plugin through the "Plugins" menu in WordPress.
4. Go to Settings > Donation Terminal to view/regenerate the API key and, optionally, choose a
   default Give Form.
5. Give the API key and REST base URL to whoever configures the Node.js backend
   (`GIVEWP_API_KEY` and `GIVEWP_API_BASE_URL`).

== Frequently Asked Questions ==

= Does this plugin charge cards or talk to Stripe? =

No. All Stripe communication (creating PaymentIntents, connecting to the Terminal reader,
capturing payment) happens in the Node.js backend and the Android app. This plugin only records
already-completed payments as GiveWP donations.

= What happens if GiveWP isn't active? =

The plugin shows an admin notice and does not register any REST routes, so the backend's calls
will 404 until GiveWP is activated.

= Is the donation-creating endpoint idempotent? =

Yes. If a donation already exists with the same `stripePaymentIntentId` in its payment meta, the
existing donation's ID/donor ID/status are returned instead of creating a duplicate.

== Changelog ==

= 1.0.0 =
* Initial release: health check + donation-creation REST endpoints, API key settings page,
  default Give Form picker, stripe_terminal gateway label registration.
