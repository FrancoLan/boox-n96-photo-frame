# Changelog

## 1.2.0 — 2026-09-23

- Replaced white full-image margins with darkened colors sampled from the adjacent photo edges; black remains the fallback.
- Kept face-priority and full-image fallback behavior aligned with the Kindle Voyage edition.
- Added an explicit cross-device parity policy for future changes.

## 1.1.2 — 2026-09-22

- Added face-aware framing on the Mac renderer.
- Added full-image overrides for difficult group photos.
- Moved metadata into clean lower whitespace for fitted landscape photos.
- Removed stale cached renderings after a successful device synchronization.

## 1.1.1 — 2026-09-22

- Added authenticated, allowlisted wireless control.
- Added heartbeats, restricted diagnostics, and verified APK updates.
- Kept wireless ADB disabled.

## 1.0.0 — 2026-09-22

- Initial BOOX N96 photo-frame client.
- Added iCloud Shared Album synchronization, offline caching, 10–20 minute rotation, and location/time overlays.
