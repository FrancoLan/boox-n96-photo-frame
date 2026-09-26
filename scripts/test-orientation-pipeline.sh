#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$ROOT"

if rg -n "sips.*-r|normalized\.png|--orientation" server/sync.mjs; then
    echo "The sync pipeline must not rotate pixels before ImageIO applies EXIF orientation." >&2
    exit 1
fi

rg -q 'kCGImageSourceCreateThumbnailWithTransform: true' server/metadata-overlay.swift
echo "Single-pass EXIF orientation pipeline checks passed."
