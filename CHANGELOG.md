# Changelog

All notable changes to **Siesta** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Rebranded the application display name to **Siesta** (preserving the `com.greenify.reborn` package namespace and `applicationId`)
- Renamed the repository to `greenify-rea` and updated repository links and badges
- Designed a new minimalist adaptive & monochrome launcher icon for **Siesta**

### Added
- Clean-room Kotlin & Jetpack Compose Material 3 foundation (`minSdk 31`, `targetSdk 36`, `compileSdk 36`)
- Material 3 UI with Dynamic Color (Material You), Light/Dark/System themes, Edge-to-Edge, and Predictive Back
- **Hibernation** dashboard with Working Mode status banner, running/hibernated summary, search, and one-tap **Hibernate All**
- **Analyzer** screen with installed app scanning, Standby Bucket badges (`Active` → `Restricted`), and Android 11+ historical exit reasons (`ActivityManager.getHistoricalProcessExitReasons`)
- **Settings** screen with Working Mode selector (`Auto`, `Shizuku`, `Root`, `Non-Root`), Shallow Hibernation toggle, Auto-Hibernation (`WorkManager`), theme toggles, and local JSON backup import/export
- Batched **Shizuku** and **Root (`su`)** hibernation backends (eliminating the legacy O(N²) Shizuku loop and redundant per-PID `kill -9` calls)
- Headless `AutomationReceiver` (using `WorkManager`, fixing Android 12+ Background Activity Launch restrictions)
- Quick Settings `HibernateTileService` for one-tap hibernation from the notification shade
- GitHub repository scaffolding: CI workflow, issue/PR templates, Dependabot, `LICENSE`, `NOTICE.md`, `CONTRIBUTING.md`, and `SECURITY.md`
