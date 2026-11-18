<p align="center">
  <img src="assets/icon.svg" width="84" height="84" alt="Siesta icon">
</p>

<h1 align="center">Siesta</h1>

<p align="center">
  A clean-room, open-source Kotlin &amp; Material 3 reimagining of Android app hibernation for modern Android (12–16+).
</p>

<p align="center">
  <a href="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml"><img src="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <a href="https://github.com/Unnidentified/greenify-rea/releases"><img src="https://img.shields.io/github/v/release/Unnidentified/greenify-rea?include_prereleases" alt="Latest Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License: Apache 2.0"></a>
</p>

---

## Why Siesta?

Reverse-engineering the legacy `com.oasisfeng.greenify` (`5.1.1`) APK revealed several architectural bottlenecks and modern Android incompatibilities:
- **Signature-killer wrapper:** Legacy repackaged builds injected `KillerApplication` hooks around a missing native library.
- **O(N²) Shizuku force-stop loop:** Privileged Shizuku hibernation looped over the full package list inside an outer package loop.
- **Android 14/15 foreground & BAL breakages:** `IntentService`, untyped `startForeground()`, 30-minute `AlarmManager` polling, and `BroadcastReceiver.startActivity()` (blocked by Background Activity Launch rules).
- **Deprecated UI & telemetry:** Light-only `PreferenceActivity`, `ProgressDialog`, `SetupWizardLib`, and bundled Firebase/Crashlytics keys.

**Siesta** rebuilds the experience cleanly in Kotlin + Jetpack Compose + Material 3 with **zero telemetry**, **Shizuku-first** ergonomics, and **Android 12–16+** compliance.

## Key Features

- **Material 3 UI:** Dynamic Color (Material You), System/Light/Dark themes, Edge-to-Edge layout, and Predictive Back support.
- **Three-Tab Workflow:**
  - **Hibernation:** Live working-mode card, curated hibernated apps list, standby bucket badges, search, and one-tap **Hibernate All**.
  - **Analyzer:** Installed app scanner with running/stopped indicators, Android Standby Bucket status, and `ApplicationExitInfo` diagnostics.
  - **Settings:** Working mode (`Auto`, `Shizuku`, `Root`, `Non-Root`), Shallow Hibernation, `WorkManager` Auto-Hibernation, theme preferences, and local JSON import/export.
- **Quick Settings Tile:** One-tap *Hibernate Now* tile directly in the Android Quick Settings shade.
- **Privacy First:** No Firebase, no Crashlytics, no ad/analytics SDKs, and modern Android `data_extraction_rules`.

## Tech Stack

- **Language:** Kotlin 2.4
- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **Concurrency & Scheduling:** Kotlin Coroutines, `WorkManager`
- **Privileged Backends:** Shizuku (`dev.rikka.shizuku:api` & `provider`), Root (`su` batched `am force-stop`), `UsageStatsManager`
- **SDK Targets:** `minSdk 31` (Android 12) · `targetSdk 36` · `compileSdk 36`

## Building Locally

### Prerequisites
- **JDK 21** (OpenJDK / Temurin 21)
- **Android SDK** with `platforms;android-36` and `build-tools;36.0.0`

### Commands

```bash
git clone https://github.com/Unnidentified/greenify-rea.git
cd greenify-rea

# Run unit tests, Android Lint, and build debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Install debug build on connected device or emulator
./gradlew installDebug
```

## Project Structure

```text
app/src/main/java/com/greenify/reborn/
├── data/            # Preferences, JSON backup/export, and app repository
├── engine/          # Shizuku, Root, UsageStats, WorkManager, and TileService
├── model/           # Domain models (AppEntry, WorkingMode, StandbyBucket, ExitReason)
└── ui/
    ├── analyzer/    # App Analyzer & Insights screen + detail sheet
    ├── components/  # Reusable Material 3 cards, badges, and mode status banners
    ├── home/        # Hibernation dashboard screen
    ├── settings/    # Material 3 Settings & Onboarding/Permissions sheets
    └── theme/       # Material 3 color schemes, Dynamic Color, typography
```

## Contributing & License

- See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines and PR workflow.
- See [SECURITY.md](SECURITY.md) for responsible vulnerability reporting.
- Licensed under the [Apache License 2.0](LICENSE). See [NOTICE.md](NOTICE.md) for upstream attribution.
