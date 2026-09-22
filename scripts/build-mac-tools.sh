#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
MODULE_CACHE=${TMPDIR:-/tmp}/boox-photoframe-swift-modules
mkdir -p "$MODULE_CACHE"

/usr/bin/swiftc -module-cache-path "$MODULE_CACHE" \
    "$ROOT_DIR/server/metadata-overlay.swift" \
    -framework AppKit -framework CoreImage -framework Vision -framework ImageIO \
    -o "$ROOT_DIR/server/metadata-overlay"

/usr/bin/swiftc -parse-as-library -module-cache-path "$MODULE_CACHE" \
    "$ROOT_DIR/server/reverse-geocode.swift" \
    -framework Foundation -framework MapKit \
    -o "$ROOT_DIR/server/reverse-geocode"

echo "Built server/metadata-overlay and server/reverse-geocode"
