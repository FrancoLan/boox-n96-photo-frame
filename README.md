# BOOX N96 Photo Frame

Turn an ONYX BOOX N96 running Android 4.0.4 into a low-power Wi-Fi photo frame backed by an iCloud Shared Album.

The Mac companion downloads the shared album, reverse-geocodes photo coordinates, renders 1072×1448 grayscale images with location and capture time, and serves a checksum-verified manifest. The Android client keeps an offline cache and changes photos every 10–20 minutes.

## Highlights

- Runs on the original BOOX N96 firmware: Android 4.0.4 / API 15.
- Uses the same public iCloud Shared Album on every display.
- Face-aware portrait framing. Wide group photos fall back to full-image display instead of cutting off people; unused areas use darkened colors sampled from the adjacent photo edges, with black as the fallback.
- Location and local capture time are rendered into the lower-right corner.
- Offline cache, boot start, physical page keys, touch navigation, and automatic stale-cache cleanup.
- Restricted wireless controls without wireless ADB or a remote shell.
- APK downloads are length-checked and SHA-256 verified before Android's installer asks for local confirmation.

## Architecture

```text
iCloud Shared Album
        │ HTTPS
        ▼
Mac: sync.mjs ──▶ grayscale PNG cache ──▶ authenticated HTTP server
                                                    │ local Wi-Fi
                                                    ▼
                                      BOOX N96 Android client
                                      ├─ verified offline cache
                                      ├─ 10–20 minute rotation
                                      └─ restricted control poller
```

## Tested configuration

- ONYX BOOX N96, firmware 1.9.1 (`2018-09-18_19-36_1.9.1_41e5d42`)
- Android 4.0.4, API 15
- macOS on Apple silicon
- Node.js 18 or newer
- Android SDK Platform 36, Build Tools 36.0.0, and platform-tools

Other BOOX models may work, but the screen size, old Android networking behavior, and E Ink refresh behavior have only been verified on the N96.

## Quick start

1. Make an iCloud Shared Album public and copy its public URL.
2. Install Node.js 18+, Xcode command-line tools, and Android SDK tools.
3. Prepare the Mac configuration:

```sh
cp server/config.example.json server/config.local.json
```

Edit `server/config.local.json`:

- Set `publicAlbumURL` to the public share URL.
- Set `listenHost` to the Mac's LAN address, not `127.0.0.1` or `0.0.0.0`.
- Keep `dataDir` and `authTokenFile` outside any public repository if you customize them.

4. Build the native Mac helpers, synchronize once, and start the server:

```sh
./scripts/build-mac-tools.sh
./scripts/sync-now.sh
./scripts/run-server.sh
```

The server creates a random 256-bit token in `data/server-token` on first start.

5. Connect the BOOX by USB, enable USB debugging, authorize the Mac, and install:

```sh
./scripts/install-android.sh
```

If more than one Android device is connected, set `BOOX_SERIAL` explicitly.

## Controls

On the device:

- Tap the right half, swipe left, or use the forward page key for the next photo.
- Tap the left half or use the back page key for the previous photo.
- Press Android Back to leave the photo view. The wireless control service remains available.

From the Mac:

```sh
./scripts/manage.sh status
./scripts/manage.sh next
./scripts/manage.sh previous
./scripts/manage.sh sync
./scripts/manage.sh restart
./scripts/manage.sh disable
./scripts/manage.sh enable
./scripts/manage.sh diagnose
./scripts/manage.sh update
./scripts/manage.sh clear
```

The device polls every 60 seconds, so commands are not immediate. `update` publishes a signed APK and opens Android's installer on the BOOX; installation still requires confirmation on the device.

## Photo framing overrides

The renderer uses macOS Vision and Core Image face detection. If an unusually angled or partial face is missed, add the photo's iCloud record ID to `fitPhotoIds`:

```json
"fitPhotoIds": [
  "PHOTO-RECORD-ID"
]
```

After synchronization, the BOOX removes cached renderings that are no longer in the current manifest.

Shared photo-frame behavior is kept in parity with the Kindle Voyage edition. Any intentional user-visible divergence must be approved explicitly; see [Cross-device parity](docs/CROSS_DEVICE_PARITY.md).

## Security model

This project is designed for a trusted private LAN:

- Every photo, manifest, status, command, diagnostic, and APK request requires the same 256-bit bearer token.
- Wireless commands are allowlisted, short-lived, uniquely identified, and deduplicated.
- There is no wireless ADB setup and no arbitrary command execution.
- Diagnostics exclude photos, books, and the authentication token.

The local HTTP transport is not encrypted. Do not port-forward it to the internet or use it on an untrusted Wi-Fi network. Use a private VPN such as WireGuard or Tailscale for remote access. See [SECURITY.md](SECURITY.md).

## Repository layout

- `android/`: API-15-compatible Android client and reproducible build script.
- `server/`: iCloud synchronization, renderer, authenticated server, and control CLI.
- `scripts/`: build, install, synchronization, server, and management wrappers.
- `docs/HANDOVER.zh-CN.md`: operational handover and recovery guide.
- `docs/PROTOCOL.md`: manifest and wireless-control protocol.

Before a public commit, run `./scripts/check.sh`. It validates shell, Node, JSON, native Mac helpers, the Android APK, and common private-value patterns.

## License

MIT. See [LICENSE](LICENSE).
