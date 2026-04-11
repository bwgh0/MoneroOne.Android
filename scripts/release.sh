#!/bin/bash
set -euo pipefail

# Usage: ./scripts/release.sh <build-number> [version]
# Example: ./scripts/release.sh 4 1.0.4

BUILD="${1:?Usage: ./scripts/release.sh <build-number> [version]}"
VERSION="${2:-$(grep 'versionName' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')}"
TAG="v${VERSION}-${BUILD}"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
  echo "Error: must be on main branch (currently on $BRANCH)"
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Error: uncommitted changes exist"
  exit 1
fi

echo "Creating release: $TAG (version $VERSION, build $BUILD)"
echo ""

git tag "$TAG"
git push origin "$TAG"

echo ""
echo "Tag $TAG pushed — CI will build and upload to Google Play"
