#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_LIB="${HOMEDIR_ENV_LIB:-${SCRIPT_DIR}/homedir-env-lib.sh}"
ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ ! -f "${ENV_LIB}" ]]; then
  ENV_LIB="/usr/local/bin/homedir-env-lib.sh"
fi
# shellcheck source=/dev/null
source "${ENV_LIB}"
homedir_env_load "${ENV_FILE}"

AUTO_DEPLOY_ENABLED="${AUTO_DEPLOY_ENABLED:-true}"
if [[ "${AUTO_DEPLOY_ENABLED,,}" != "true" ]]; then
  exit 0
fi

PRIMARY_IMAGE_REPO="${IMAGE_REPO:-quay.io/sergio_canales_e/homedir}"
IMAGE_REPOSITORIES="${IMAGE_REPOSITORIES:-${PRIMARY_IMAGE_REPO} ghcr.io/os-santiago/homedir}"
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

resolved_tag_info="$(
  python3 - "$AUTO_DEPLOY_TAG_LIMIT" "$AUTO_DEPLOY_TIMEOUT_SECONDS" <<'PY'
import json
import os
import re
import sys
import urllib.request

limit_raw, timeout_raw = sys.argv[1:3]
limit = max(1, min(int(limit_raw), 500))
timeout = max(5, min(int(timeout_raw), 60))

semver = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")
best_key = None
best_tag = ""
best_repo = ""

for raw_repo in os.environ.get("IMAGE_REPOSITORIES", "").replace(",", " ").split():
    repo = raw_repo.strip()
    if not repo:
        continue
    if repo.startswith("quay.io/"):
        parts = repo.split("/", 2)
        if len(parts) != 3:
            continue
        url = (
            f"https://quay.io/api/v1/repository/{parts[1]}/{parts[2]}/tag/"
            f"?onlyActiveTags=true&limit={limit}&page=1"
        )
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            payload = json.load(resp)
        tag_names = [((tag or {}).get("name") or "").strip() for tag in payload.get("tags", [])]
    else:
        url = f"https://{repo.split('/', 1)[0]}/v2/{repo.split('/', 1)[1]}/tags/list?n={limit}"
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(request, timeout=timeout) as resp:
            payload = json.load(resp)
        tag_names = [str(tag).strip() for tag in payload.get("tags", [])]

    for name in tag_names:
        if not name or name == "latest":
            continue
        match = semver.fullmatch(name)
        if not match:
            continue
        key = tuple(int(part) for part in match.groups())
        if best_key is None or key > best_key:
            best_key = key
            best_tag = name[1:] if name.startswith("v") else name
            best_repo = repo

print(f"{best_repo} {best_tag}".strip(), end="")
PY
)"

latest_repo="$(awk '{print $1}' <<<"$resolved_tag_info")"
latest_tag="$(awk '{print $2}' <<<"$resolved_tag_info")"

if [[ -z "${latest_tag:-}" ]]; then
  log "no semver tag resolved for repos=${IMAGE_REPOSITORIES}"
  notify_alert "WARN" \
    "Auto-deploy warning" \
    "No semver tag resolved from configured registries" \
    "repos=${IMAGE_REPOSITORIES}"
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

if [[ "${current_tag:-}" == "$latest_tag" ]]; then
  log "up-to-date current_tag=${current_tag} latest_repo=${latest_repo}"
  exit 0
fi

log "detected new tag current_tag=${current_tag:-none} latest_tag=${latest_tag} latest_repo=${latest_repo}"
notify_alert "WARN" \
  "Auto-deploy triggered fallback" \
  "New image tag detected by polling fallback" \
  "current=${current_tag:-none} latest=${latest_tag} repo=${latest_repo}"
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
