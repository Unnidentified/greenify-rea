# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| `0.2.x` | Yes       |
| `0.1.x` | Legacy    |

## Reporting a Vulnerability

Do not open public issues for security vulnerabilities. This is especially important for bugs involving privileged (`Shizuku` / `su`) command execution, package name sanitization, `RecentsSwipeMonitorService`, exported `BroadcastReceiver` / `ContentProvider` surfaces, or intent-injection vectors.

Instead, report vulnerabilities privately via GitHub Security Advisories or directly to the repository maintainers with:
- Affected Android API level and device/emulator environment
- Working mode (`Shizuku`, `Root`, or `Non-Root`)
- Minimal reproduction steps and impact assessment
