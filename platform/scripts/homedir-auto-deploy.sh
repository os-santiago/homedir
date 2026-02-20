#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

AUTO_DEPLOY_ENABLED="${AUTO_DEPLOY_ENABLED:-true}"
if [[ "${AUTO_DEPLOY_ENABLED,,}" != "true" ]]; then
  exit 0
fi

IMAGE_REPO="${IMAGE_REPO:-quay.io/sergio_canales_e/homedir}"
UPDATE_SCRIPT="${UPDATE_SCRIPT:-/usr/local/bin/homedir-update.sh}"
ALERT_SCRIPT="${ALERT_SCRIPT:-/usr/local/bin/homedir-discord-alert.sh}"
AUTO_LOGFILE="${AUTO_DEPLOY_LOGFILE:-/var/log/homedir-auto-deploy.log}"
AUTO_DEPLOY_TAG_LIMIT="${AUTO_DEPLOY_TAG_LIMIT:-100}"
AUTO_DEPLOY_TIMEOUT_SECONDS="${AUTO_DEPLOY_TIMEOUT_SECONDS:-15}"
CONTAINER_NAME="${CONTAINER_NAME:-homedir}"

log() {
  echo "$(date -Iseconds): $*" >> "$AUTO_LOGFILE"
}

notify_alert() {
  local severity="$1"
  local title="$2"
  local message="$3"
  local details="${4:-}"
  if [[ -x "$ALERT_SCRIPT" ]]; then
    "$ALERT_SCRIPT" "$severity" "$title" "$message" "$details" >/dev/null 2>&1 || true
  fi
}

if [[ ! "$IMAGE_REPO" =~ ^quay\.io/([^/]+)/([^/:]+)$ ]]; then
  log "invalid IMAGE_REPO format: ${IMAGE_REPO}"
  notify_alert "FAIL" \
    "Auto-deploy configuration error" \
    "Invalid IMAGE_REPO format" \
    "IMAGE_REPO=${IMAGE_REPO}"
  exit 1
fi

repo_namespace="${BASH_REMATCH[1]}"
repo_name="${BASH_REMATCH[2]}"

resolved_tag_info="$(
  python3 - "$repo_namespace" "$repo_name" "$AUTO_DEPLOY_TAG_LIMIT" "$AUTO_DEPLOY_TIMEOUT_SECONDS" <<'PY'
import json
import re
import sys
import urllib.request

namespace, repo, limit_raw, timeout_raw = sys.argv[1:5]
limit = max(1, min(int(limit_raw), 500))
timeout = max(5, min(int(timeout_raw), 60))

url = (
    f"https://quay.io/api/v1/repository/{namespace}/{repo}/tag/"
    f"?onlyActiveTags=true&limit={limit}&page=1"
)
with urllib.request.urlopen(url, timeout=timeout) as resp:
    payload = json.load(resp)

semver = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")
best_key = None
best_tag = ""
best_digest = ""

for tag in payload.get("tags", []):
    entry = tag or {}
    name = entry.get("name", "").strip()
    if not name or name == "latest":
        continue
    match = semver.fullmatch(name)
    if not match:
        continue
    key = tuple(int(part) for part in match.groups())
    if best_key is None or key > best_key:
        best_key = key
        best_tag = name[1:] if name.startswith("v") else name
        best_digest = (entry.get("manifest_digest") or "").strip()

print(f"{best_tag} {best_digest}".strip(), end="")
PY
)"

latest_tag="$(awk '{print $1}' <<<"$resolved_tag_info")"
latest_digest="$(awk '{print $2}' <<<"$resolved_tag_info")"

if [[ -z "${latest_tag:-}" ]]; then
  log "no semver tag resolved for repo=${IMAGE_REPO}"
  notify_alert "WARN" \
    "Auto-deploy warning" \
    "No semver tag resolved from registry" \
    "repo=${IMAGE_REPO}"
  exit 1
fi

current_tag="$(
  podman ps --filter "name=${CONTAINER_NAME}" --format '{{.Image}}' \
    | sed -n '1s/.*://p'
)"

current_image_id="$(podman inspect -f '{{.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
current_digest=""
if [[ -n "${current_image_id:-}" ]]; then
  current_digest="$(podman image inspect "$current_image_id" --format '{{.Digest}}' 2>/dev/null || true)"
fi

if [[ -n "${latest_digest:-}" && -n "${current_digest:-}" && "${latest_digest}" == "${current_digest}" ]]; then
  log "up-to-date digest=${current_digest} current_tag=${current_tag:-none} latest_tag=${latest_tag}"
  exit 0
fi

if [[ "${current_tag:-}" == "$latest_tag" ]]; then
  log "up-to-date current_tag=${current_tag}"
  exit 0
fi

log "detected new tag current_tag=${current_tag:-none} latest_tag=${latest_tag}"
notify_alert "WARN" \
  "Auto-deploy triggered fallback" \
  "New image tag detected by polling fallback" \
  "current=${current_tag:-none} latest=${latest_tag}"
if DEPLOY_TRIGGER=auto-deploy "$UPDATE_SCRIPT" "$latest_tag"; then
  log "auto-deploy success tag=${latest_tag}"
  notify_alert "RECOVERY" \
    "Auto-deploy completed" \
    "Fallback deploy applied successfully" \
    "tag=${latest_tag}"
  exit 0
fi

log "auto-deploy failed tag=${latest_tag}"
notify_alert "FAIL" \
  "Auto-deploy failed" \
  "Fallback deploy could not be applied" \
  "tag=${latest_tag}"
exit 1
