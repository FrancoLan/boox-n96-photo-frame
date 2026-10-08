# BOOX N96 maintenance handover

Updated 2026-10-08. This records the last verified deployment; query fresh device reports for live status. Earlier investigations remain in Git history. Private deployment records are not published.

## Current release and remaining checks

- Release [v1.2.6](https://github.com/FrancoLan/boox-n96-photo-frame/releases/tag/v1.2.6) is published from `main`, with the original-key-signed APK and SHA-256 checksum. Android versionCode is 12; PR #3 merged as `281b096`.
- The N96 runs Android 4.0.4 / API 15 and firmware 1.9.1. Heartbeats report battery percentage and power-source state; diagnostics add temperature, voltage, level and scale. Missing readings remain unknown.
- Complete project checks, Java fallback regressions, APK builds and CI passed. Physical tests confirmed photo-transition full refresh, new backup-endpoint diagnostics after Ethernet removal, and new primary-endpoint diagnostics after reconnection.
- Final dual-interface settings have not been reboot-tested. Router reservations/exclusions require separate verification; an unanswered ping does not prove an address is reserved.

## Shared behavior and hardware differences

Both clients use the same iCloud shared album, check the manifest every minute, show each photo for a random 10–20 minutes, keep offline caches, verify integrity and remove obsolete cached renders. New photos play once with priority before the previous order resumes. Shared rendering uses one EXIF orientation transform, face-aware framing or full-image display when needed, dark edge-colored fill, separate location and local capture-time lines with weekdays, and restrained shadow lifting.

BOOX supports touch and physical navigation keys and startup on boot. About 100 ms after drawing, it calls the firmware's `View.fullRefreshScreen()`; it also refreshes on resume and cancels old callbacks on another photo or pause. The user confirmed its effect matches the physical Settings key. Kindle retains a full refresh ten seconds after quick manual navigation. The ordinary N96 has no frontlight and does not emulate Kindle lighting policy. See [CROSS_DEVICE_PARITY.md](CROSS_DEVICE_PARITY.md).

## Network and automatic charging

- Ethernet and Wi-Fi on the Mac use different stable LAN addresses, with Ethernet first in the service order. Verify router reservations. The shared server's `listenHosts` contains only explicit addresses; vanished listeners are removed and restored when their addresses return.
- In `config.properties`, `server_url` is the primary and optional `server_fallback_url` is the backup. Only HTTP origins are supported, without paths, queries or user information. Retain the existing token.
- Management polling and photo synchronization probe an authenticated manifest, use the backup only if the primary fails, and retry the primary on later cycles. If both fail, retain caches and retry. Other projects need their own fallback configuration.
- Verify physical unplug/reconnect tests using fresh diagnostics and the server's `localAddress`. Old heartbeats cannot prove current connectivity. An unauthenticated HTTP 401 indicates a reachable endpoint.
- The shared Mac charging controller switches on strictly below 40% and off strictly above 80%, holding state between those thresholds. It checks every minute and accepts telemetry up to ten minutes old. Separate private JSONL logs record second-precision command results, charging transitions and five-minute charging samples; unavailable readings never reuse old battery values.
- All four charging shortcut directions, background shutdown and locked-session shutdown were physically tested. Closing subprocess stdin fixed background timeouts. Keep the Mac powered on, logged in, connected and awake. See [Kindle CHARGING.md](https://github.com/FrancoLan/kindle-voyage-photo-frame/blob/main/docs/CHARGING.md).

## Installation, signing and troubleshooting

For upgrades, explicitly set `BOOX_SIGNING_KEYSTORE` to the original private key matching the installed APK and compare signing certificates. The repository default or CI temporary debug key cannot be assumed compatible with an in-place upgrade. Never commit signing keys. Updates verify size and SHA-256 but still require confirmation in the device's Android installer; wireless ADB stays disabled.

Use `scripts/manage.sh status` to inspect `receivedAt`, `appState`, version, battery and `localAddress`. If reports stop, check device Wi-Fi, Mac addresses/listeners and sleep state before server logs. Queue one `diagnose` when evidence is needed, wait for completion, and avoid replacing pending commands. If cached photos remain but updates stop, check Mac synchronization, request `sync`, then query fresh status.

While USB storage is exported, Android's sdcard can become unreadable. After modifying configuration on the Mac-mounted volume, verify readback and safely eject before starting Photoframe. Do not delete the entire sdcard or factory-reset to resolve ordinary synchronization problems. Preserve `config.properties` before clearing caches; server and device tokens must match.

## Maintenance, backup and release rules

- Identify the deployed runtime and LaunchAgents first. Public installers and existing deployments may use different locations; avoid blind reinstalls or duplicate services. A periodic task exiting successfully between runs is healthy.
- Back up configuration, signing keys, code and logs privately with checksums. Restore only necessary files, retain current network configuration and tokens, and verify fresh reports afterward.
- `main` is the release branch. Use short-lived branches and PRs with the full `scripts/check.sh`, CI and relevant physical testing before merging. Do not force-push the primary branch or move release tags.
- Maintain shared behavior through linked Kindle PRs and obtain approval for new user-visible differences. The shared server, charging logs and interface binding are included in [Kindle v0.4.11](https://github.com/FrancoLan/kindle-voyage-photo-frame/releases/tag/v0.4.11).
- Publish only code, tests, sanitized documentation and verified release assets. Actual network configuration, album links, photos, tokens, diagnostics, battery logs, full device identifiers and private keys stay local. Never modify iCloud originals.
- Keep GitHub documentation, PR titles/descriptions and release notes in English. Update current sections directly; preserve detailed history in Git or private records instead of appending conflicting current-state summaries.
