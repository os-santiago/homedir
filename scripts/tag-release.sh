#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
POM_FILE="$REPO_ROOT/quarkus-app/pom.xml"
README_FILE="$REPO_ROOT/README.md"

# Extract version from pom.xml
VERSION=$(grep -m1 '<version>' "$POM_FILE" | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
if [[ -z "$VERSION" ]]; then
  echo "Could not determine version from $POM_FILE" >&2
  exit 1
fi

# Extract description from README.md
DESCRIPTION=$(grep -m1 -vE '^(#|\[|$)' "$README_FILE" | sed -E 's/^\s+//;s/\s+$//')
if [[ -z "$DESCRIPTION" ]]; then
  echo "Could not determine description from $README_FILE" >&2
  exit 1
fi

TAG="$VERSION"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists"
  exit 0
fi

git tag -a "$TAG" -m "$DESCRIPTION"
git push origin "$TAG"
