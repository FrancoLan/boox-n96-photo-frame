#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
CONFIG_JSON=${PHOTOFRAME_CONFIG:-$ROOT_DIR/server/config.local.json}
NODE=${NODE:-$(command -v node)}

cd "$ROOT_DIR"
exec "$NODE" "$ROOT_DIR/server/manage-boox.mjs" "$CONFIG_JSON" "$@"
