#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Keep an explicitly configured JDK and otherwise use the standard JDK 21 installation.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
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
MAP_SRC="app/build/outputs/mapping/release/mapping.txt"
APK_DST="dist/Tapwing-v${VERSION_NAME}-release.apk"
AAB_DST="dist/Tapwing-v${VERSION_NAME}-release.aab"
MAP_DST="dist/Tapwing-v${VERSION_NAME}-mapping.txt"

cp "$APK_SRC" "$APK_DST"
cp "$AAB_SRC" "$AAB_DST"
if [ -f "$MAP_SRC" ]; then
    cp "$MAP_SRC" "$MAP_DST"
fi

APKSIGNER=""
if [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER=$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name apksigner | sort -V | tail -n 1 || true)
fi

echo "=== 3. Verifying Release Signatures & Play Store 16 KB Alignment ==="
if [ -n "$APKSIGNER" ]; then
    "$APKSIGNER" verify --verbose --print-certs "$APK_DST"
fi
jarsigner -verify -verbose -certs "$AAB_DST" | tail -n 20

python3 - "$AAB_DST" <<'PY'
import struct, sys, zipfile

aab = sys.argv[1]
bad = []
checked = 0
with zipfile.ZipFile(aab) as zf:
    for name in zf.namelist():
        if not name.endswith(".so"):
            continue
        data = zf.read(name)
        if len(data) < 64 or data[:4] != b"\x7fELF":
            continue
        if data[4] != 2:  # Only 64-bit ELF requires 16 KB alignment on Google Play
            continue
        checked += 1
        e_phoff = struct.unpack_from("<Q", data, 32)[0]
        e_phentsize, e_phnum = struct.unpack_from("<HH", data, 54)
        aligns = []
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            p_type, _, _, _, _, _, _, p_align = struct.unpack_from("<IIQQQQQQ", data, off)
            if p_type == 1:
                aligns.append(p_align)
        if not aligns or any(a < 16384 for a in aligns):
            bad.append((name, aligns))

print(f"Verified {checked} 64-bit .so libraries in {aab}: 16KB_ALIGN_OK={len(bad) == 0}")
if bad:
    for name, aligns in bad:
        print(f"ERROR: {name} PT_LOAD alignment < 16KB: {aligns}")
    sys.exit(1)
PY

echo "=== 4. Generating SHA256SUMS.txt ==="
(
    cd dist
    if [ -f "Tapwing-v${VERSION_NAME}-mapping.txt" ]; then
        sha256sum "Tapwing-v${VERSION_NAME}-release.apk" "Tapwing-v${VERSION_NAME}-release.aab" "Tapwing-v${VERSION_NAME}-mapping.txt" > SHA256SUMS.txt
    else
        sha256sum "Tapwing-v${VERSION_NAME}-release.apk" "Tapwing-v${VERSION_NAME}-release.aab" > SHA256SUMS.txt
    fi
)
cat dist/SHA256SUMS.txt
ls -lh dist/
