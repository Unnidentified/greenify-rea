# <img src="assets/icon.svg" width="85" height="85" align="absmiddle" alt="greenify-rea icon" /> greenify-rea

[![Android CI](https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Unnidentified/greenify-rea/actions/workflows/android-ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/Unnidentified/greenify-rea?include_prereleases)](https://github.com/Unnidentified/greenify-rea/releases)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Android 12–16+](https://img.shields.io/badge/Android-12%E2%80%9316%2B-3DDC84)

Open-source Android app hibernation tool for Android 12+, built from scratch in Kotlin and Jetpack Compose. See [requirements](#requirements) first.

When you "nap" an app, Android stops its background processes and moves it into a stricter Standby Bucket so it stays quiet until you open it again. You can trigger a nap with one tap beside the search bar, run scheduled passes with `WorkManager`, or let the Recents monitor force-stop apps right when you swipe them away.

Grab the signed APK from [Releases](https://github.com/Unnidentified/greenify-rea/releases/latest) or download a CI build from the [Actions](https://github.com/Unnidentified/greenify-rea/actions) tab.

---

## Why this exists

Old Android hibernation apps ran into two walls on modern Android versions:

- **Slow kill loops:** Legacy tools fired separate `am force-stop` and per-PID `kill -9` commands in nested `O(N^2)` loops over Shizuku, locking up on large app lists.
- **Modern API breakages:** Background `IntentService` calls, untyped foreground services, legacy `AlarmManager` wakeups, and Background Activity Launches all break on Android 12 through 16+.
- **Missing media guards:** Stopping every background package blindly kills music players or apps you left open in Recents on purpose.
- **Telemetry and dated UI:** Many older builds ship analytics SDKs and light-only preference screens.

`greenify-rea` (installed as **Greenify**) replaces that stack in Kotlin and Jetpack Compose with linear `O(N)` command batching, modern Android background APIs, and zero telemetry.

## How it works under the hood

| Part | What it does | Implementation notes |
|---|---|---|
| Command builder | Turns your app list and per-app rules into linear `O(N)` shell calls | `ShellCommandBuilder` emits `am force-stop --user` and `am set-standby-bucket --user`, skipping protected system packages and `EXEMPT` apps |
| Privilege backends | Executes batched commands over one channel | Runs via Shizuku (`ShizukuUserService` AIDL) or a single Root (`su`) shell session without spawning a new shell per app |
| Recents & Audio guard | Keeps music and open Recents tasks alive during a nap | Reads `dumpsys activity recents`, `dumpsys media_session`, `dumpsys audio`, and `AudioManager` before building the stop list |
| Recents swipe monitor | Closes swiped-away apps automatically | `RecentsSwipeMonitorService` compares `dumpsys activity recents` snapshots (gated by `UsageStatsManager` transitions and a 650ms trampoline debounce) |
| AppOps & components | Cuts off wakeups without uninstalling apps | Uses `cmd appops` (`RUN_ANY_IN_BACKGROUND`, `WAKE_LOCK`, `START_FOREGROUND`, `SCHEDULE_EXACT_ALARM`) and `pm disable-user` / `pm enable` per component |
| Wake-up tracker | Shows why an app woke back up | Correlates `UsageStatsManager` events and `ApplicationExitInfo` inside the per-app sheet and batch wake logs |
| UI & graphics | Keeps scrolling responsive at 120–165Hz | Single-activity Compose UI (`Home` + `Settings`), AGSL RenderEffect blur/refraction with idle pass gating, `6 MiB` capped `AppIconCache`, and `FlowyBottomSheet` overlays |

### Per-app rules and Recents swipe-close

Tapping any app opens an in-window drawer (`FlowyBottomSheet`) with three tabs (`Adjustments`, `Wake Services`, `Wake Logs`). Inside Adjustments you can pick a hibernation mode (`Global Default`, `Deep Force-Stop`, `Shallow Standby`, `Exempt`), override Recents/Audio protection, set **Auto-Close on Recents Swipe** (`Follow Global`, `Auto-Close on Swipe`, `Never Auto-Close`), change the Standby Bucket immediately, or toggle AppOps wake-up cut-offs.

Because `RecentsSwipeMonitorService.shouldMonitor` checks both the global switch and every per-app rule, setting even one app to **Auto-Close on Swipe** keeps the lightweight monitor active on boot and after app updates while ignoring everything else.

Long-pressing an app or tapping the checklist icon enters **Batch Selector** mode for multi-app hibernation, policy overrides, AppOps edits, and category component cut-offs.

## Requirements

| Requirement | Why |
|---|---|
| Android 12 or newer (`minSdk 31`, `targetSdk 36`) | Required for modern `ApplicationExitInfo`, splash screen attributes, Standby Buckets, and AGSL shaders (Android 13+ for runtime glass refraction) |
| Shizuku **or** Root (`su`) | Non-root mode can inspect apps and open system App Info pages, while silent force-stop, Standby Buckets, AppOps, and component disabling require [Shizuku](https://shizuku.rikka.app/) or root (`su` via KernelSU, SukiSU-Nova, Magisk, or APatch) |
| Usage Access (optional) | Needed for wake-up history attribution and screen-awake usage gating inside `RecentsSwipeMonitorService` |
| Notifications (optional) | Lets the **Quick Action** notification and **Recents Swipe Auto-Close** foreground service post their status |

Both Shizuku and Root can be authorized at the same time. Switch between `Auto`, `Shizuku`, `Root (su)`, and `Non-Root` anytime in **Settings**.

## Building locally

You need **JDK 21** and Android SDK (`platforms;android-36`, `build-tools;36.0.0`).

```bash
git clone https://github.com/Unnidentified/greenify-rea.git
cd greenify-rea

# Run unit tests, Android Lint, and assemble the debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Install directly onto a connected device or emulator
./gradlew installDebug
```

## Project layout

```text
app/src/main/java/com/greenify/reborn/
├── data/            # Preferences, JSON backup/export, Raw Engine Log store, app repository
├── engine/          # Shizuku, Root, Recents/Audio detection, swipe monitor, WorkManager, Quick Tile
├── model/           # Domain models (AppEntry, AppFilterChip, PerAppPolicy, SwipeCloseMode, AppOpRestriction)
└── ui/
    ├── components/  # Splash overlay, custom switches, Liquid Glass bottom bar, acrylic status blur, flowy sheets
    ├── home/        # Stackable filters, quick Nap button, batch selector, per-app & batch adjustment sheets
    ├── settings/    # Dual privileges, Working Mode, theme & Menu Bar Tint, Raw Engine Logs viewer
    └── theme/       # Seven themes (Follow System default, Forest Dark, Sage Light, Mist Light, Midnight Dark, Light, Dark)
docs/                # Lowercase docs (changelog, contributing, notice, security)
```

## Documentation & License

- [Changelog](docs/changelog.md)
- [Contributing Guidelines](docs/contributing.md)
- [Security Policy](docs/security.md)
- [Attribution & Notice](docs/notice.md)
- Licensed under the [Apache License 2.0](LICENSE).
