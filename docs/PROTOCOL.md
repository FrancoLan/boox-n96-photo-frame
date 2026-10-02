# Protocol

All endpoints require `Authorization: Bearer <64 lowercase hex characters>`.

## Photo manifest

`GET /v1/manifest` returns tab-separated data:

```text
# kindle-photoframe-manifest-v1<TAB>VERSION<TAB>COUNT
SHA256<TAB>BYTE_LENGTH<TAB>/v1/images/SHA256.png
```

The historical manifest marker retains the `kindle-photoframe` name for wire compatibility. Each image is fetched only from its exact hash-derived path and is accepted only when both byte length and SHA-256 match.

## Device control

The BOOX polls `GET /v1/boox/control/command` every 60 seconds. Commands contain a UUID, action, creation time, and expiry time. Supported actions are:

- `next`
- `previous`
- `sync`
- `restart`
- `disable`
- `enable`
- `update`
- `diagnose`

The client rejects unknown actions, malformed identifiers, future commands, expired commands, and previously executed identifiers.

Status is posted to `POST /v1/boox/control/status`. Diagnostics use gzip and are limited to one MiB. APK updates are served from a SHA-256-derived path and verified before Android's package installer is opened.

## Battery diagnostics (Android 1.2.5 / code 11)

The authenticated status `detail` includes `battery` (integer percent or `unknown`), `power` (`charging`, `discharging`, `not-charging`, `full`, or `unknown`), and `plugged` (Android power-source bitmask, 0 for battery, -1 for unavailable). Existing status fields and polling intervals are unchanged.

The gzip diagnose report adds `batteryAvailable`, `batteryPercent`, `batteryLevel`, `batteryScale`, `batteryStatus`, `batteryPlugged`, `batteryTemperatureTenthsC`, and `batteryVoltageMv`. Missing or invalid level/scale is reported as `unknown`, never zero. Temperature uses tenths of a degree Celsius, voltage uses millivolts; -1 means unavailable. Reads use the API 15 sticky `ACTION_BATTERY_CHANGED` snapshot without additional permissions or a persistent listener. Full and plugged-in states do not imply active charging.
