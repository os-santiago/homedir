#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

TAG="$VERSION"

git tag -a "$TAG" -m "EventFlow $VERSION"
git push origin "$TAG"
