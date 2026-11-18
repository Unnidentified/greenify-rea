# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| `0.1.x` | Yes       |

## Reporting a Vulnerability

Please do not open public issues for security vulnerabilities—especially issues involving privileged (`Shizuku` / `su`) command execution, exported `BroadcastReceiver` / `ContentProvider` surfaces, or intent-injection vectors.

Instead, report vulnerabilities privately via GitHub Security Advisories or directly to the repository maintainers, including:
- Affected Android API level and device/emulator environment
- Working mode (`Shizuku`, `Root`, or `Non-Root`)
- Minimal reproduction steps and impact assessment
