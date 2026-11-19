<table>
  <tr>
    <td align="center" valign="middle" width="190">
      <div align="center">
        <img src="assets/icon.svg" width="88" height="88" alt="Greenify icon"><br><br>
        <strong>greenify-rea</strong>
      </div>
    </td>
    <td align="center" valign="middle">
      <p align="center">
        <a href="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml"><img src="https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
        <a href="https://github.com/Unnidentified/greenify-rea/releases"><img src="https://img.shields.io/github/v/release/Unnidentified/greenify-rea?include_prereleases" alt="Latest Release"></a>
        <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License: Apache 2.0"></a>
        <img src="https://img.shields.io/badge/Android-12%E2%80%9316%2B-3DDC84" alt="Android 12-16+">
      </p>
      <p align="center">
        Clean-room, open-source Kotlin &amp; Jetpack Compose reimagining of Android app hibernation for modern Android (12–16+) — with dual Shizuku &amp; Root (<code>su</code> / KernelSU / SukiSU / Magisk) authorization, Recents &amp; active audio protection, per-app &amp; batch wake-up service cut-offs, raw engine logs, and a Liquid Glass UI.
      </p>
    </td>
  </tr>
</table>

---

## Why greenify-rea?

Legacy Android hibernation tools accumulated architectural bottlenecks and modern Android incompatibilities over time:
- **O(N²) Shizuku force-stop loops** and redundant per-PID kill commands.
- **Android 12–16+ breakages** around `IntentService`, untyped `startForeground()`, `AlarmManager` wake-locks, and Background Activity Launch (BAL) restrictions.
- **No Recents / active audio guard** — hibernating cached apps still open in Recents or actively playing music.
- **Deprecated UI & telemetry** built on light-only `PreferenceActivity` screens and analytics SDKs.

**greenify-rea** (installed as **Greenify**) rebuilds the workflow cleanly in Kotlin + Jetpack Compose with **zero telemetry**, **dual Shizuku & Root first-class support**, and **Android 12–16+** compliance.

## Key Features

- **Miuix-Inspired UI, Elastic Bounce & Liquid Glass Navigation:**
  - Floating **Liquid Glass** bottom capsule bar with interactive drag/tap switching, capsule rim refraction, and optional toggle back to a solid bar.
  - **7 Built-In Themes:** **Follow System** (default on first install), **Forest Dark**, **Sage Light**, **Mist Light**, **Midnight Dark**, **Light**, and **Dark**, plus **Dynamic Accent (Monet)** enabled by default.
  - Smooth spring overscroll bounce and pull-to-refresh with live loading indicator.
- **Composable Stackable Filters & Batch Selector:**
  - Stackable filter pills (**User**, **System**, **Awake**, **Napping**, **In Recents**, **Wake-ups**, **Configured**) that layer together with at least one filter always active.
  - **Single-tap** any app for detailed adjustments, AppOps restrictions, individual wake-up service/receiver cut-offs, and wake logs.
  - **Long-press** or tap the checklist icon to enter **Batch Selector** mode (with **Select All**, batch hibernation, batch policy & AppOps adjustments, category wake-up cut-offs, and combined wake logs).
- **Recents & Active Audio Player Protection:**
  - Automatically detects apps currently open in the Android **Recents** screen (`dumpsys activity recents`) and active **audio/media players** (`dumpsys media_session`, `dumpsys audio`, `AudioManager`), shielding them from hibernation while open (configurable globally and per-app).
- **Dual Privileges, Full Greenify Settings & Raw Engine Logs:**
  - Grant both **Shizuku** and **Root (`su`)** independently and switch **Working Mode** (`Auto`, `Shizuku`, `Root (su)`, `Non-Root`) at any time.
  - Complete modern configuration: Shallow Naps, Target Standby Bucket, Ignore Background-Free, Scheduled Naps (`WorkManager` + Screen-Off-only option), Quick Action Notification, System Apps, Extras for Geek, JSON Backup/Restore, and a full **Raw Engine Logs** viewer in Settings with one-tap **Copy All**.
- **Quick Settings Tile:** One-tap *Hibernate Now* tile directly in the Android Quick Settings shade.
- **Privacy First:** Zero Firebase, zero Crashlytics, zero ad/analytics SDKs, and modern Android backup rules.

## Tech Stack

- **Language:** Kotlin 2.4 (`JVM 21`)
- **UI:** Jetpack Compose, Material 3, Custom Liquid Glass RenderEffect shaders (`AGSL`)
- **Concurrency & Scheduling:** Kotlin Coroutines, `WorkManager`
- **Privileged Backends:** Shizuku (`dev.rikka.shizuku:api` & `provider` + `ShizukuProvider`), Root (`su` single-session batched commands), `UsageStatsManager`, `cmd appops`, `pm disable-user`
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
├── data/            # Preferences, JSON backup/export, Raw Engine Log store, app repository
├── engine/          # Shizuku, Root, Recents/Audio detection, WorkManager, and TileService
├── model/           # Domain models (AppEntry, AppFilterChip, PerAppPolicy, AppOpRestriction, etc.)
└── ui/
    ├── components/  # Status banners, Liquid Glass bottom bar, chips, and search bar
    ├── home/        # Composable filters, batch selector, per-app & batch adjustment/wake-log sheets
    ├── settings/    # Dual privileges, Working Mode, Greenify settings, Raw Engine Logs viewer
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
