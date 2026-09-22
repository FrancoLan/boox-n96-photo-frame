#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
CONFIG_JSON=${PHOTOFRAME_CONFIG:-$ROOT_DIR/server/config.local.json}
NODE=${NODE:-$(command -v node)}
cd "$ROOT_DIR"

if [ -n "${ADB:-}" ]; then
    ADB_BIN=$ADB
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    ADB_BIN="$ANDROID_SDK_ROOT/platform-tools/adb"
elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
else
    ADB_BIN=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
fi

[ -f "$CONFIG_JSON" ] || { echo "Missing $CONFIG_JSON; copy server/config.example.json first." >&2; exit 2; }
[ -x "$ADB_BIN" ] || { echo "adb not found; set ADB or ANDROID_SDK_ROOT." >&2; exit 2; }

SERIAL=${BOOX_SERIAL:-}
if [ -z "$SERIAL" ]; then
    SERIAL=$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1 }' | head -1)
fi
[ -n "$SERIAL" ] || { echo "No authorized Android device found." >&2; exit 2; }

SERVER_URL=$($NODE -e '
const c = require(process.argv[1]);
if (!c.listenHost || c.listenHost === "127.0.0.1" || c.listenHost === "0.0.0.0") throw new Error("listenHost must be the Mac LAN address");
process.stdout.write(`http://${c.listenHost}:${c.port || 8787}`);
' "$CONFIG_JSON")
TOKEN_PATH=$($NODE -e '
const path = require("node:path");
const c = require(process.argv[1]);
process.stdout.write(path.resolve(c.authTokenFile));
' "$CONFIG_JSON")
[ -f "$TOKEN_PATH" ] || { echo "Missing token file $TOKEN_PATH; start the server once first." >&2; exit 2; }
AUTH_TOKEN=$(tr -d '\r\n' < "$TOKEN_PATH")

"$ROOT_DIR/android/build.sh"
DEVICE_CONFIG="$ROOT_DIR/android/build/config.properties"
umask 077
printf '%s\n' \
    "server_url=$SERVER_URL" \
    "auth_token=$AUTH_TOKEN" \
    "interval_min_seconds=600" \
    "interval_max_seconds=1200" \
    "autostart=true" > "$DEVICE_CONFIG"

"$ADB_BIN" -s "$SERIAL" install -r "$ROOT_DIR/android/build/boox-photoframe.apk"
"$ADB_BIN" -s "$SERIAL" shell mkdir -p /sdcard/BooxPhotoframe/cache
"$ADB_BIN" -s "$SERIAL" push "$DEVICE_CONFIG" /sdcard/BooxPhotoframe/config.properties >/dev/null
"$ADB_BIN" -s "$SERIAL" shell am start -n io.github.francolan.booxphotoframe/.MainActivity
