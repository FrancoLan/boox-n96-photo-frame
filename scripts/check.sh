#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$ROOT_DIR"

sh -n android/build.sh scripts/*.sh
node --check server/icloud-source.mjs
node --check server/sync.mjs
node --check server/server.mjs
node --check server/manage-boox.mjs
jq empty server/config.example.json

matches=$(rg -n --hidden -S \
    '(photos\.icloud\.com/shared/album/|/Users/[^/]+/|0123456789ABCDEF|BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|[0-9a-f]{64})' \
    . --glob '!.git/**' --glob '!android/build/**' --glob '!server/metadata-overlay' --glob '!server/reverse-geocode' --glob '!scripts/check.sh' || true)
matches=$(printf '%s\n' "$matches" | rg -v 'REPLACE_WITH_YOUR_PUBLIC_SHARE_KEY' || true)
if [ -n "$matches" ]; then
    printf '%s\n' "$matches"
    echo "Potential private value found." >&2
    exit 1
fi

./scripts/build-mac-tools.sh
./server/metadata-overlay --tone-tests
./android/build.sh
echo "Checks passed."
