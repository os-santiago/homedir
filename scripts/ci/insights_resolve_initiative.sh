#!/usr/bin/env bash

set -euo pipefail

pr_number="${1:-}"
pr_title="${2:-}"
pr_branch="${3:-}"
pr_labels_json="${4:-[]}"

sanitize_token() {
  local raw="${1:-}"
  local normalized
  normalized="$(echo "${raw}" \
    | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[^a-z0-9._\/-]+/-/g; s/[._\/]+/-/g; s/-+/-/g; s/^-+//; s/-+$//')"
  if [ "${#normalized}" -gt 64 ]; then
    normalized="${normalized:0:64}"
    normalized="${normalized%%-}"
  fi
  echo "${normalized}"
}

extract_from_labels() {
  local labels_json="${1:-[]}"
  if [ -z "${labels_json}" ] || [ "${labels_json}" = "null" ]; then
    return 0
  fi

  if command -v jq >/dev/null 2>&1; then
    echo "${labels_json}" \
      | jq -r '.[]? // empty' 2>/dev/null \
      | while IFS= read -r label; do
          lower_label="$(echo "${label}" | tr '[:upper:]' '[:lower:]')"
          case "${lower_label}" in
            initiative:*|insights:*)
              echo "${label#*:}"
              return 0
              ;;
          esac
        done
  fi
}

extract_scope_from_title() {
  local title="${1:-}"
  if [[ "${title}" =~ ^[a-zA-Z0-9_-]+\(([a-zA-Z0-9._/-]+)\): ]]; then
    echo "${BASH_REMATCH[1]}"
  fi
}

extract_token_from_branch() {
  local branch="${1:-}"
  if [ -z "${branch}" ]; then
    return 0
  fi
  if [[ "${branch}" =~ ^[a-zA-Z0-9._-]+/([a-zA-Z0-9._/-]+)$ ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo "${branch}"
  fi
}

resolved_token=""
if [ -z "${resolved_token}" ]; then
  resolved_token="$(extract_from_labels "${pr_labels_json}" | head -n1)"
fi
if [ -z "${resolved_token}" ]; then
  resolved_token="$(extract_scope_from_title "${pr_title}")"
fi
if [ -z "${resolved_token}" ]; then
  resolved_token="$(extract_token_from_branch "${pr_branch}")"
fi

safe_token="$(sanitize_token "${resolved_token}")"
if [ -n "${safe_token}" ]; then
  echo "initiative-${safe_token}"
  exit 0
fi

if [ -n "${pr_number}" ]; then
  echo "pr-${pr_number}"
  exit 0
fi

echo "initiative-unknown"
exit 0
