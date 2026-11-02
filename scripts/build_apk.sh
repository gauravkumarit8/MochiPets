#!/usr/bin/env bash
set -e
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/MochiPets-debug.apk
ls -lh dist/MochiPets-debug.apk
echo "APK ready: dist/MochiPets-debug.apk"
