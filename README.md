<table>
  <tr>
    <td align="center" valign="middle" width="170">
      <img src="assets/icon.svg" width="88" height="88" alt="Greenify icon"><br>
      <h2 align="center">greenify-rea</h2>
    </td>
    <td align="left" valign="top">
      <p align="left">
        <a href="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml"><img src="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
        <a href="https://github.com/Unnidentified/greenify-rea/releases"><img src="https://img.shields.io/github/v/release/Unnidentified/greenify-rea?include_prereleases" alt="Latest Release"></a>
        <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License: Apache 2.0"></a>
        <img src="https://img.shields.io/badge/Android-12%E2%80%9316%2B-3DDC84" alt="Android 12-16+">
      </p>
      <p align="left">
        Clean-room, open-source Kotlin &amp; Jetpack Compose reimagining of Android app hibernation for modern Android (12–16+) — with Shizuku-first execution, Root (<code>su</code> / KernelSU / SukiSU / Magisk) &amp; Standby Bucket modes, wake-up diagnostics, and a Liquid Glass UI.
      </p>
    </td>
  </tr>
</table>

---

## Why greenify-rea?

Legacy Android hibernation tools accumulated architectural bottlenecks and modern Android incompatibilities over time:
- **O(N²) Shizuku force-stop loops** and redundant per-PID kill commands.
- **Android 12–16+ breakages** around `IntentService`, untyped `startForeground()`, `AlarmManager` wake-locks, and Background Activity Launch (BAL) restrictions.
- **Deprecated UI & telemetry** built on light-only `PreferenceActivity` screens and analytics SDKs.

**greenify-rea** (installed as **Greenify**) rebuilds the workflow cleanly in Kotlin + Jetpack Compose with **zero telemetry**, **Shizuku & Root first-class support**, and **Android 12–16+** compliance.

## Key Features

- **Miuix-Inspired UI & Liquid Glass Navigation:**
  - Floating **Liquid Glass** bottom capsule bar with interactive drag/tap switching, capsule rim refraction, and optional toggle back to a solid bar.
  - **6 Built-In Themes:** **Forest Dark** (default on first install), **Sage Light**, **Mist Light**, **Midnight Dark**, **Light**, **Dark**, and **Follow System**, plus optional **Dynamic Accent (Monet)**.
- **Two-Tab Focused Workflow:**
  - **Home (`greenify-rea`):** Live status banner (**All set** when authorized / actionable guidance when degraded), `Managed` · `All apps` · `Awake` filters, instant search, standby bucket chips, one-tap hibernation, and long-press wake-up diagnostics.
  - **Settings:** Interactive **Privileges & Onboarding** (`Shizuku`, `Root (su)` verification, direct package-scoped `Usage Access`, `Notifications`), **Working Mode** (`Auto`, `Shizuku`, `Root`, `Non-Root`), **Shallow Naps**, **Scheduled Naps** (`WorkManager`), **Appearance**, and JSON backup import/export.
- **Quick Settings Tile:** One-tap *Hibernate Now* tile directly in the Android Quick Settings shade.
- **Privacy First:** Zero Firebase, zero Crashlytics, zero ad/analytics SDKs, and modern Android backup rules.

## Tech Stack

- **Language:** Kotlin 2.4 (`JVM 21`)
- **UI:** Jetpack Compose, Material 3, Custom Liquid Glass RenderEffect shaders (`AGSL`)
- **Concurrency & Scheduling:** Kotlin Coroutines, `WorkManager`
- **Privileged Backends:** Shizuku (`dev.rikka.shizuku:api` & `provider` + `ShizukuProvider`), Root (`su` batched `am force-stop`), `UsageStatsManager`
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
├── data/            # Preferences, JSON backup/export, environment & app repository
├── engine/          # Shizuku, Root, UsageStats, WorkManager, and TileService
├── model/           # Domain models (AppEntry, WorkingMode, ThemeMode, StandbyBucket)
└── ui/
    ├── components/  # Status banners, Liquid Glass bottom bar, chips, and search bar
    ├── home/        # Hibernation & Wake-Up Diagnostics dashboard
    ├── settings/    # Privileges, Nap Engine, Appearance, Backup, and About screens
    └── theme/       # Miuix-inspired light/dark/tinted palettes & Dynamic Color
docs/
├── CHANGELOG.md     # Release history
├── CONTRIBUTING.md  # Development setup & PR guidelines
├── NOTICE.md        # Upstream trademark & attribution notice
└── SECURITY.md      # Vulnerability reporting policy
```

## Documentation & License

- [Changelog](docs/CHANGELOG.md)
- [Contributing Guidelines](docs/CONTRIBUTING.md)
- [Security Policy](docs/SECURITY.md)
- [Attribution & Notice](docs/NOTICE.md)
- Licensed under the [Apache License 2.0](LICENSE).
