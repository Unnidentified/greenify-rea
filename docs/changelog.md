# Changelog

All notable changes to **Greenify (`greenify-rea`)** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-beta.3] - 2026-11-20

### Added
- **Quick Nap Action next to Home Search Bar:** Placed a circular moon **Nap** button directly beside `MiuixSearchBar` to hibernate all awake, non-exempt matching apps in one tap (useful right after installs or app updates).
- **Per-App Auto-Close on Recents Swipe:** Added `SwipeCloseMode` (`Follow Global`, `Auto-Close on Swipe`, `Never Auto-Close`) to both single-app and batch adjustment sheets, plus `RecentsSwipeMonitorService` (`UsageStatsManager`-gated diff checks with 650ms debounce and boot/update sync). Works even when the global swipe-close switch is off if any app chooses `Auto-Close on Swipe`.
- **Menu Bar Tint Toggle:** Added a **Menu Bar Tint** switch under **Settings → Appearance** (off by default) that blends `primaryContainer` accents into both the floating capsule and active droplet, persisted in JSON backups.

### Changed
- **Nuvio Bar Scroll Collapse & Idle Gating:** Expanded `LiquidBottomTabs` scroll-triggered capsule collapse and gated offscreen Layer 2/Layer 3 blur and lens passes strictly behind `isGlassLensActive`, while caching base blur/vibrancy `RenderEffect` objects separately from bar dimensions.
- **Single Continuous Outline-Draw Splash:** Replaced the Android 12+ system splash icon with transparent `@drawable/ic_splash_empty` across day and night themes so only `AnimatedLogoSplashOverlay` draws the leaf path continuously via `PathMeasure` before filling cleanly.
- **Drawer Open, List Composition & Top Bounce Physics:** Synced `FlowyBottomSheet` opening springs after initial layout via `withFrameNanos`, isolated `AppDetailsUiState` out of `MainUiState` so detail loading never invalidates the Home list mid-slide, replaced `Material3 Switch` in `GreenifySwitch` with a lightweight toggleable pill switch, replaced per-row `DateUtils` and nested `Surface` chips in `AppListRow`, softened `rememberBoundaryBounceOffset` (`maxBouncePx = 56.dp`), and scaled fling cushion velocity across display densities with a `0.35f` pull-to-refresh guard.

### Fixed
- **Duplicate Launch Icon & Stutter:** Eliminated the double-icon handoff on cold launch, first-frame app config drawer hitch, mid-scroll jank after opening, and missed top-reach bounce activation during upward scrolls.

## [0.2.0-beta.2] - 2026-11-19

### Added
- **Composable Stackable Filters:** Replaced mutually exclusive tabs with stackable filter pills (**User**, **System**, **Awake**, **Napping**, **In Recents**, **Wake-ups**, **Configured**) with smooth edge fading on horizontal borders.
- **Batch Selector Mode:** Long-press any app or tap the checklist icon to multi-select apps, toggle **Select All**, and run batch hibernation, policy overrides, AppOps cut-offs, category wake-up cut-offs, and combined wake logs without any FAB.
- **Full Per-App & Batch Cut-offs:** Added AppOps wake-up cut-offs (`RUN_ANY_IN_BACKGROUND`, `WAKE_LOCK`, `START_FOREGROUND`, `SCHEDULE_EXACT_ALARM`) and individual component/service/receiver disable-and-restore controls in the app detail sheet.
- **Recents & Active Audio Guard:** Protects apps open in Android Recents (`dumpsys activity recents`) and active media players (`dumpsys media_session`, `dumpsys audio`, `AudioManager`), configurable globally and per app.
- **Raw Engine Logs Viewer:** Added `AppLogStore` and an in-app Settings sheet (`Raw Engine Logs`) with level badges, command/exit detail cards, copy, and clear actions.
- **Progressive Acrylic Status Bar:** Added an AGSL/RenderEffect progressive blur and subtle light-reactive frosted grain beneath the transparent status bar with smooth meniscus transitions and zero pre-entry color haloes.
- **Flowy In-Window Bottom Sheets:** Replaced separate-window `ModalBottomSheet` dialogs with `FlowyBottomSheet` overlays running at native display refresh rate with spring open/close physics, swipe/nested-scroll dismiss, and fully transparent edge-to-edge system bars.

### Changed
- **Defaults:** Enabled **Follow System** theme, **Dynamic Accent (Monet)**, and **Force Max Refresh Rate** by default on fresh installs.
- **Home Visual Polish & Icon:** Updated app icon, adaptive monochrome icon, and Home bottom bar tab icon to the new leaf vector (`leaf-svgrepo-com.svg`); updated Home subtitle to `N = awake + napping`; removed middle pull-to-refresh overlay spinner in favor of the top-right spinning refresh icon; improved deactivated switch visibility; centered README badge layout.
- **CPU & RAM Optimization:** Downsampled and byte-budgeted `AppIconCache` (6 MiB cap + `onTrimMemory`), batch-queried launchable packages, eliminated per-frame `drawGlassPill` native allocations, removed unconditional idle Choreographer/sensor render loops (`~0.3%` settled idle CPU, `0` idle frames), parallelized app detail loading without AppOps height jumps, and moved log persistence off-thread.

### Fixed
- Eliminated overscroll bounce jitter on pull-to-refresh and scroll-to-top rebound, AppOps loading flash/button jumps on fast sheet scrolls, and opaque navigation-bar artifacts behind sheets.

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
- **Documentation Layout:** Organized documentation files under `docs/`.

### Fixed
- **Shizuku Service Detection:** Declared `rikka.shizuku.ShizukuProvider` in `AndroidManifest.xml` and added sticky binder received/dead listeners so Shizuku Manager delivers binders immediately upon authorization.
- **Usage Access & Notifications Refresh:** Automatically re-inspects environment state when returning from system Usage Access or Android notification permission dialogs.

## [0.1.0-reborn] - 2026-11-18

### Added
- Clean-room Kotlin & Jetpack Compose Material 3 foundation (`minSdk 31`, `targetSdk 36`, `compileSdk 36`).
- Batched **Shizuku** (`ShizukuUserService`) and **Root (`su`)** hibernation backends eliminating legacy `O(N^2)` loops and redundant `kill -9` calls.
- Headless `AutomationReceiver` scheduled via `WorkManager` and Quick Settings `HibernateTileService`.
- GitHub CI workflow, issue/PR templates, Dependabot configuration, and Apache 2.0 license.
