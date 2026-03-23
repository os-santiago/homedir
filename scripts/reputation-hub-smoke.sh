#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-https://homedir.opensourcesantiago.io}}"
PROFILE="${2:-${PROFILE:-baseline-off}}"

usage() {
  cat <<'EOF'
Usage:
  scripts/reputation-hub-smoke.sh [base_url] [profile]

Profiles:
  baseline-off   Production baseline when Reputation Hub rollout flags are OFF.
  primary-on     Expected behavior after primary replacement is fully enabled.
EOF
}

http_code() {
  local mode="$1"
  local path="$2"
  local url="${BASE_URL}${path}"
  case "$mode" in
    get)
      curl -sS -o /dev/null -w '%{http_code}' "$url"
      ;;
    head)
      curl -sS -I -o /dev/null -w '%{http_code}' "$url"
      ;;
    follow)
      curl -sS -L -o /dev/null -w '%{http_code}' "$url"
      ;;
    *)
      echo "Unknown mode: $mode" >&2
      exit 2
      ;;
  esac
}

declare -a checks
case "$PROFILE" in
  baseline-off)
    checks=(
      "get|/|200"
      "get|/comunidad|200"
      "get|/eventos|200"
      "get|/proyectos|200"
      "get|/comunidad/reputation-hub|404"
      "get|/comunidad/reputation-hub/how|404"
      "head|/community/reputation-hub|303"
      "follow|/community/reputation-hub|404"
      "head|/community/reputation-hub/how|303"
      "follow|/community/reputation-hub/how|404"
    )
    ;;
  primary-on)
    checks=(
      "get|/|200"
      "get|/comunidad|200"
      "get|/eventos|200"
      "get|/proyectos|200"
      "get|/comunidad/reputation-hub|200"
      "get|/comunidad/reputation-hub/how|200"
      "head|/comunidad/board|303"
      "follow|/comunidad/board|200"
      "head|/community/reputation-hub|303"
      "follow|/community/reputation-hub|200"
      "head|/community/reputation-hub/how|303"
      "follow|/community/reputation-hub/how|200"
    )
    ;;
  *)
    echo "Unsupported profile: $PROFILE" >&2
    usage
    exit 2
    ;;
esac

echo "Reputation Hub smoke"
echo "  base_url: $BASE_URL"
echo "  profile:  $PROFILE"
echo

failures=0
for check in "${checks[@]}"; do
  IFS='|' read -r mode path expected <<<"$check"
  actual="$(http_code "$mode" "$path")"
  if [[ "$actual" == "$expected" ]]; then
    printf 'PASS  %-7s %-42s expected=%s actual=%s\n' "$mode" "$path" "$expected" "$actual"
  else
    printf 'FAIL  %-7s %-42s expected=%s actual=%s\n' "$mode" "$path" "$expected" "$actual"
    failures=$((failures + 1))
  fi
done

echo
if [[ "$failures" -gt 0 ]]; then
  echo "Smoke failed: $failures mismatch(es)." >&2
  exit 1
fi

echo "Smoke passed."
