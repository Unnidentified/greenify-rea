# Changelog

All notable changes to **Greenify (`greenify-rea`)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-beta.1] - 2026-11-19

### Added
- **Liquid Glass Bottom Bar:** Interactive Miuix/SukiSU-inspired floating capsule bar supporting tap & drag switching, velocity stretch, press magnification, capsule rim refraction, and drag cancellation.
- **Six Built-In Themes + Monet:** Added tinted pairs (**Sage Light** / **Forest Dark** and **Mist Light** / **Midnight Dark**) alongside **Light**, **Dark**, and **Follow System**, plus an optional **Dynamic Accent (Monet)** toggle.
- **Positive Status Card (`All set`):** Replaced the degraded red warning banner with a semantic green **All set** card displaying the active hibernation backend once authorized.
- **Wake-Up Diagnostics:** Integrated `UsageStatsManager` wake-up event attribution and Android `ApplicationExitInfo` inspection into a long-press app detail sheet.
- **Root Verification Action:** Added an explicit **Verify** action in Settings (`su -c id` probe and `$PATH` resolution) for KernelSU, SukiSU-Nova, Magisk, and APatch.

### Changed
- **Default First-Install Theme:** Set **Forest Dark** as the default theme on first install (with Monet dynamic override disabled by default so the Forest Dark palette displays cleanly).
- **Streamlined Two-Tab UI:** Consolidated navigation into **Home** (Managed / All apps / Awake filter chips, search, swipe-free focus) and **Settings**.
- **Branding:** Named the launcher app **Greenify**, in-app branding **greenify-rea**, and repository `greenify-rea`.
- **Direct Usage Access Flow:** Updated the **Open** shortcut in Settings to deep-link directly to Greenify's Usage Access toggle.
- **Documentation Layout:** Moved `CHANGELOG.md`, `CONTRIBUTING.md`, `NOTICE.md`, and `SECURITY.md` into `docs/`.

### Fixed
- **Shizuku Service Detection:** Declared `rikka.shizuku.ShizukuProvider` in `AndroidManifest.xml` and added sticky binder received/dead listeners so Shizuku Manager delivers binders immediately upon authorization.
- **Usage Access & Notifications Refresh:** Automatically re-inspects environment state when returning from system Usage Access or Android notification permission dialogs.

## [0.1.0-reborn] - 2026-11-18

### Added
- Clean-room Kotlin & Jetpack Compose Material 3 foundation (`minSdk 31`, `targetSdk 36`, `compileSdk 36`).
- Batched **Shizuku** (`ShizukuUserService`) and **Root (`su`)** hibernation backends eliminating legacy O(N²) loops and redundant `kill -9` calls.
- Headless `AutomationReceiver` scheduled via `WorkManager` and Quick Settings `HibernateTileService`.
- GitHub CI workflow, issue/PR templates, Dependabot configuration, and Apache 2.0 license.
