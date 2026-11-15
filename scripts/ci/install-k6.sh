#!/usr/bin/env bash
# Official release archive, pinned independently of runner APT/keyring changes.
# Source: https://github.com/grafana/k6/releases/tag/v2.4.0
set -euo pipefail

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 || $# != 1 ]]; then
  echo "Usage: install-k6.sh DESTINATION (Linux x86_64)" >&2
  exit 2
fi

version=v2.4.0
sha256=5a5ecf23529ccf93135bc7e5b0c1cd5126ecf5f2672e22eed1556a0ab74abb87
archive="k6-${version}-linux-amd64.tar.gz"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
  --connect-timeout 15 --max-time 120 --retry 2 --retry-max-time 180 \
  --output "$work/$archive" "https://github.com/grafana/k6/releases/download/$version/$archive"
printf '%s  %s\n' "$sha256" "$work/$archive" | sha256sum --check --status
# Extract only the expected executable after the complete archive is verified.
tar -xzf "$work/$archive" -C "$work" "k6-${version}-linux-amd64/k6"
mkdir -p "$1"
install -m 0755 "$work/k6-${version}-linux-amd64/k6" "$1/k6"
"$1/k6" version
