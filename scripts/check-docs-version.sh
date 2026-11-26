#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION_NAME=$(grep -E 'versionName\s*=' app/build.gradle.kts | head -n 1 | cut -d'"' -f2)
VERSION_CODE=$(grep -E 'versionCode\s*=' app/build.gradle.kts | head -n 1 | tr -dc '0-9')

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
    echo "ERROR: Could not read versionName / versionCode from app/build.gradle.kts"
    exit 1
fi

echo "Checking docs consistency for v${VERSION_NAME} (versionCode=${VERSION_CODE})..."

FILES=(
    "README.md"
    "docs/README.en.md"
    "CHANGELOG.md"
    "PLAY_STORE_GUIDE.md"
    "docs/play-store/DATA_SAFETY_AND_REVIEW.md"
    "docs/play-store/STORE_LISTING_DE.md"
    "docs/play-store/STORE_LISTING_EN.md"
)

FAILED=0
for f in "${FILES[@]}"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: Missing expected doc file: $f"
        FAILED=1
        continue
    fi
    if ! grep -qF "$VERSION_NAME" "$f"; then
        echo "ERROR: $f does not mention version $VERSION_NAME"
        FAILED=1
    fi
done

if ! grep -qF "versionCode = ${VERSION_CODE}" docs/play-store/DATA_SAFETY_AND_REVIEW.md; then
    echo "ERROR: docs/play-store/DATA_SAFETY_AND_REVIEW.md does not mention versionCode = ${VERSION_CODE}"
    FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi

echo "OK: All docs match v${VERSION_NAME} (versionCode=${VERSION_CODE})."
