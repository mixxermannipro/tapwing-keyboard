#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Prefer JDK 21 (required by Android Gradle Plugin 8.13)
if [ -d "/home/manuelblasaditsch/.local/share/rea/jdk-21.0.13" ]; then
    export JAVA_HOME="/home/manuelblasaditsch/.local/share/rea/jdk-21.0.13"
elif [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
fi
if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Android/sdk"
fi

VERSION_NAME=$(grep -E 'versionName\s*=' app/build.gradle.kts | head -n 1 | cut -d'"' -f2)
VERSION_NAME="${VERSION_NAME:-1.0.0}"

echo "=== 1. Running Tapwing Unit Tests ==="
./gradlew testDebugUnitTest --no-daemon

echo "=== 2. Building Signed Release APK & AAB ==="
./gradlew assembleRelease bundleRelease --no-daemon

mkdir -p dist
APK_SRC="app/build/outputs/apk/release/app-release.apk"
AAB_SRC="app/build/outputs/bundle/release/app-release.aab"
APK_DST="dist/Tapwing-v${VERSION_NAME}-release.apk"
AAB_DST="dist/Tapwing-v${VERSION_NAME}-release.aab"

cp "$APK_SRC" "$APK_DST"
cp "$AAB_SRC" "$AAB_DST"

APKSIGNER=""
if [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER=$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name apksigner | sort -V | tail -n 1 || true)
fi

echo "=== 3. Verifying Release Signatures ==="
if [ -n "$APKSIGNER" ]; then
    "$APKSIGNER" verify --verbose --print-certs "$APK_DST"
fi
jarsigner -verify -verbose -certs "$AAB_DST" | tail -n 20

echo "=== 4. Generating SHA256SUMS.txt ==="
(
    cd dist
    sha256sum "Tapwing-v${VERSION_NAME}-release.apk" "Tapwing-v${VERSION_NAME}-release.aab" > SHA256SUMS.txt
)
cat dist/SHA256SUMS.txt
ls -lh dist/
