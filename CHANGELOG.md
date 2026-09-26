# Changelog

## Unreleased

- Apply the shared Mac renderer's gentle shadow lift only to distinctly dark photos; preserve black points and highlights and leave normally exposed photos unchanged.
- Deployed to the Mac renderer on 2026-09-27; regenerated and published all 44 shared photos as manifest `4c78d24e6f4712b6` for BOOX and Kindle.

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
