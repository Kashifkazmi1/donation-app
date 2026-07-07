# Donation Terminal (Android)

Native Android app for nonprofit staff/volunteers to take in-person card donations with a Stripe
Terminal M2 Bluetooth reader and push them into GiveWP via the project's Node.js backend. See
`../docs/API_CONTRACT.md` for the exact backend contract this app is built against.

## Opening the project

1. Install **Android Studio** (Ladybug/2024.2 or newer recommended).
2. Open the `android/` directory as an Android Studio project (not the repo root).
3. Let Gradle sync -- it needs network access to `google()` and `mavenCentral()` (declared in
   `settings.gradle.kts`) to resolve the Android Gradle Plugin, AndroidX/Compose/Hilt/Room
   libraries, and the Stripe Terminal SDK (`stripeterminal-core`, published on Maven Central).
4. Copy `local.properties.example` to `local.properties` if you need to set
   `STRIPE_PUBLISHABLE_KEY` (rarely required -- see the comments in that file); Android Studio
   manages `sdk.dir` in `local.properties` automatically.

## Requirements

- **compileSdk / targetSdk 34**, **minSdk 26** (Stripe Terminal SDK requirement).
- JDK 17 (matches `compileOptions`/`kotlinOptions` in `app/build.gradle.kts`).
- A physical Android device is strongly recommended for testing the Bluetooth reader flow --
  the emulator has no Bluetooth radio, so Reader Connection / Payment Processing can only be
  exercised on real hardware with a Stripe Terminal M2 (or the Stripe simulated reader).

## Pointing the app at a backend

The backend base URL is **not** hardcoded. On first launch, log in, then go to
**Dashboard -> Settings** and set:

- **API Base URL** -- e.g. `https://your-backend.example.org/api/v1/` (must include the `/api/v1/`
  prefix per the contract). For a backend running on your laptop while testing on the Android
  emulator, use `http://10.0.2.2:<port>/api/v1/` (already whitelisted for cleartext traffic in
  `res/xml/network_security_config.xml`).
- **Terminal Location ID** -- the Stripe Terminal `tml_...` location ID readers connect to.
- **Currency** and **Test/Live Mode** -- cosmetic/default values used when creating a donation.

Saving a changed Base URL prompts an app restart (Retrofit's base URL is fixed at process start).

## Building from the command line

```
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Architecture at a glance

MVVM + Clean Architecture, single `:app` module, packages layered as `domain` (models, repository
interfaces, pure validation use cases) / `data` (Retrofit + Moshi DTOs, Room cache, DataStore +
EncryptedSharedPreferences, the Stripe Terminal SDK wrapper in `data/terminal/TerminalManager`) /
`ui` (Jetpack Compose + Material 3 screens, one `@HiltViewModel` per screen, Navigation Compose).

## Stripe Terminal SDK version note

This project pins `stripeterminal-core` to **5.6.0** (see `gradle/libs.versions.toml`), the
current major version as of this writing. `TerminalManager.kt` uses the v5 API surface: a single
`Terminal.connectReader(...)` for all reader types, the reader listener supplied inside
`ConnectionConfiguration.BluetoothConnectionConfiguration(...)` rather than as a separate
callback parameter, and `MobileReaderListener`/`TerminalErrorCode` as top-level types. This
sandbox has no Android SDK/Gradle network access to actually compile against the real SDK jar, so
before your first build, diff `TerminalManager.kt` against the current SDK's sample app or API
reference at https://github.com/stripe/stripe-terminal-android and its `CHANGELOG.md` -- the
call sites were written against that changelog's documented signatures, not a compiler.
