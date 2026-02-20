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
AUTO_LOGFILE="${AUTO_DEPLOY_LOGFILE:-/var/log/homedir-auto-deploy.log}"
AUTO_DEPLOY_TAG_LIMIT="${AUTO_DEPLOY_TAG_LIMIT:-100}"
AUTO_DEPLOY_TIMEOUT_SECONDS="${AUTO_DEPLOY_TIMEOUT_SECONDS:-15}"

log() {
  echo "$(date -Iseconds): $*" >> "$AUTO_LOGFILE"
}

if [[ ! "$IMAGE_REPO" =~ ^quay\.io/([^/]+)/([^/:]+)$ ]]; then
  log "invalid IMAGE_REPO format: ${IMAGE_REPO}"
  exit 1
fi

repo_namespace="${BASH_REMATCH[1]}"
repo_name="${BASH_REMATCH[2]}"

latest_tag="$(
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

for tag in payload.get("tags", []):
    name = (tag or {}).get("name", "").strip()
    if not name or name == "latest":
        continue
    match = semver.fullmatch(name)
    if not match:
        continue
    key = tuple(int(part) for part in match.groups())
    if best_key is None or key > best_key:
        best_key = key
        best_tag = name[1:] if name.startswith("v") else name

print(best_tag, end="")
PY
)"

if [[ -z "${latest_tag:-}" ]]; then
  log "no semver tag resolved for repo=${IMAGE_REPO}"
  exit 1
fi

current_tag="$(
  podman ps --filter "name=${CONTAINER_NAME:-homedir}" --format '{{.Image}}' \
    | sed -n '1s/.*://p'
)"

if [[ "${current_tag:-}" == "$latest_tag" ]]; then
  log "up-to-date current_tag=${current_tag}"
  exit 0
fi

log "detected new tag current_tag=${current_tag:-none} latest_tag=${latest_tag}"
if "$UPDATE_SCRIPT" "$latest_tag"; then
  log "auto-deploy success tag=${latest_tag}"
  exit 0
fi

log "auto-deploy failed tag=${latest_tag}"
exit 1
