# Contributing to Greenify Reborn

Thank you for contributing to **Greenify Reborn**!

## Development Setup

1. **JDK:** Java 21 (Temurin / OpenJDK 21)
2. **Android SDK:** Compile SDK 36, Build Tools 36.0.0, Min SDK 31 (Android 12+)
3. **Build & Test:**
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

## Guidelines

- Keep all code in clean, idiomatic Kotlin with Material 3 / Jetpack Compose.
- Do not add analytics, crash telemetry SDKs, ad SDKs, or signature-spoofing wrappers.
- Keep privileged operations (`Shizuku` / `Root`) strictly isolated behind `HibernationBackend` interfaces and unit-testable domain models.
- Never commit keystores, local SDK paths (`local.properties`), or extracted/decompiled third-party binaries.
- Target Android 12–16+ compatibility: avoid deprecated `PreferenceActivity`, `IntentService`, `AsyncTask`, and Background Activity Launches from `BroadcastReceiver`.

## Pull Requests

1. Fork the repository and create a feature branch from `main`.
2. Ensure `./gradlew testDebugUnitTest lintDebug` passes locally.
3. Fill out the PR template and link any relevant issue.
