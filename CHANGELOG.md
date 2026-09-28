# Changelog

## 1.2.4 — 2026-09-28

- Play newly synchronized photos immediately and check shared albums every minute.
- Keep portrait photos upright with a single EXIF orientation transform; add a regression check against double rotation.
- Improve photo captions with fuller location addresses and weekday labels; apply a restrained shadow lift only to very dark photos.
- The BOOX Android client remains at 1.2.2; this release covers the shared Mac sync and rendering pipeline.

## 1.2.3 — 2026-09-26

- Apply JPEG EXIF rotation in the shared Mac renderer so portrait images remain upright on BOOX and Kindle.

## 1.2.2 — 2026-09-26

- Check for new shared-album photos every minute while the photo frame is open, independently of the slideshow interval.

## 1.2.1 — 2026-09-26

- Play newly synchronized photos immediately, then resume the existing playlist sequence.

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
