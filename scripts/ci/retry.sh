#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: retry.sh <command> [args...]" >&2
  exit 2
fi

attempts="${RETRY_ATTEMPTS:-2}"
sleep_seconds="${RETRY_SLEEP_SECONDS:-15}"

if ! [[ "$attempts" =~ ^[0-9]+$ ]] || [ "$attempts" -lt 1 ]; then
  echo "RETRY_ATTEMPTS must be an integer >= 1" >&2
  exit 2
fi

if ! [[ "$sleep_seconds" =~ ^[0-9]+$ ]] || [ "$sleep_seconds" -lt 0 ]; then
  echo "RETRY_SLEEP_SECONDS must be an integer >= 0" >&2
  exit 2
fi

for attempt in $(seq 1 "$attempts"); do
  if "$@"; then
    if [ "$attempt" -gt 1 ]; then
      echo "Command succeeded on retry ${attempt}/${attempts}."
    fi
    exit 0
  else
    status=$?
    if [ "$attempt" -ge "$attempts" ]; then
      echo "Command failed after ${attempts} attempts." >&2
      exit "$status"
    fi

    echo "Command failed (exit ${status}) on attempt ${attempt}/${attempts}. Retrying in ${sleep_seconds}s..." >&2
    sleep "$sleep_seconds"
  fi
done
