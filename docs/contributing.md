# Contributing to greenify-rea

Thank you for contributing to **Greenify (`greenify-rea`)**.

## Development Setup

1. **JDK:** Java 21 (Temurin / OpenJDK 21)
2. **Android SDK:** Compile SDK 36, Build Tools 36.0.0, Min SDK 31 (Android 12+)
3. **Build & Test:**
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

## Guidelines

- Keep all code in idiomatic Kotlin with Material 3 / Jetpack Compose.
- Never add analytics, crash telemetry SDKs, ad SDKs, or signature-spoofing wrappers.
- Keep privileged operations (`Shizuku` / `Root`) isolated behind `HibernationBackend`, `ShellCommandBuilder`, and repository interfaces so command building stays unit-testable.
- Never commit keystores, local SDK paths (`local.properties`), or decompiled third-party binaries.
- Target Android 12–16+ compatibility: avoid deprecated `PreferenceActivity`, `IntentService`, `AsyncTask`, and Background Activity Launches from `BroadcastReceiver`.
- Keep all files under `docs/` using lowercase markdown filenames (`changelog.md`, `contributing.md`, `notice.md`, `security.md`).

## Pull Requests

1. Fork the repository and create a feature branch from `main`.
2. Ensure `./gradlew testDebugUnitTest lintDebug assembleDebug` passes locally before pushing.
3. Include the PR hashtag (`#<pr>`) in PR commit messages so commit history ties cleanly to its review thread.
4. Update `docs/changelog.md` for user-facing changes and link any relevant issue.
