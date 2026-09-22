#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_ROOT=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-36/android.jar"
BUILD_DIR="$SCRIPT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"

rm -rf "$CLASSES_DIR" "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
mkdir -p "$CLASSES_DIR" "$BUILD_DIR/dex"

javac -encoding UTF-8 -source 7 -target 7 -Xlint:-options \
    -classpath "$ANDROID_JAR" -d "$CLASSES_DIR" \
    "$SCRIPT_DIR/src/io/github/francolan/booxphotoframe/MainActivity.java" \
    "$SCRIPT_DIR/src/io/github/francolan/booxphotoframe/BootReceiver.java" \
    "$SCRIPT_DIR/src/io/github/francolan/booxphotoframe/ControlService.java"

jar cf "$BUILD_DIR/classes.jar" -C "$CLASSES_DIR" .
"$BUILD_TOOLS/d8" --min-api 15 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
"$BUILD_TOOLS/aapt" package -f -M "$SCRIPT_DIR/AndroidManifest.xml" \
    -I "$ANDROID_JAR" -F "$BUILD_DIR/boox-photoframe-unsigned.apk"
(
    cd "$BUILD_DIR/dex"
    "$BUILD_TOOLS/aapt" add "$BUILD_DIR/boox-photoframe-unsigned.apk" classes.dex >/dev/null
)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/boox-photoframe-unsigned.apk" "$BUILD_DIR/boox-photoframe-aligned.apk"

if [ ! -f "$BUILD_DIR/debug.keystore" ]; then
    keytool -genkeypair -keystore "$BUILD_DIR/debug.keystore" -storepass android \
        -alias androiddebugkey -keypass android -dname "CN=BOOX Photoframe Debug,O=Local" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BUILD_TOOLS/apksigner" sign --min-sdk-version 15 \
    --ks "$BUILD_DIR/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$BUILD_DIR/boox-photoframe.apk" "$BUILD_DIR/boox-photoframe-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$BUILD_DIR/boox-photoframe.apk"
echo "$BUILD_DIR/boox-photoframe.apk"
