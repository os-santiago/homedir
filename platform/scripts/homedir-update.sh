#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_LIB="${HOMEDIR_ENV_LIB:-${SCRIPT_DIR}/homedir-env-lib.sh}"
# Optional: override with ENV_FILE=/path/to/envfile
ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ ! -f "${ENV_LIB}" ]]; then
  ENV_LIB="/usr/local/bin/homedir-env-lib.sh"
fi
# shellcheck source=/dev/null
source "${ENV_LIB}"
homedir_env_load "${ENV_FILE}"

TAG="${1:-${DEPLOY_TAG:-}}"
if [[ -z "$TAG" ]]; then
  echo "usage: homedir-update.sh <tag> (or set DEPLOY_TAG in env)" >&2
  exit 1
fi
RAW_TAG="$TAG"
if [[ "$TAG" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  TAG="${TAG#v}"
fi

REPO="${IMAGE_REPO:-quay.io/sergio_canales_e/homedir}"
IMAGE="${REPO}:${TAG}"
LOGFILE="${LOGFILE:-/var/log/homedir-update.log}"
CONTAINER="${CONTAINER_NAME:-homedir}"
DEPLOY_TRIGGER="${DEPLOY_TRIGGER:-manual}"
ALERT_SCRIPT="${ALERT_SCRIPT:-/usr/local/bin/homedir-discord-alert.sh}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
DATA_VOLUME="${DATA_VOLUME:-/work/data:/work/data:Z}"
LOCKDIR="${LOCKDIR:-/var/lock/homedir-update.lock.d}"
CONTAINER_MEMORY_LIMIT="${CONTAINER_MEMORY_LIMIT:-2g}"
CONTAINER_CPU_LIMIT="${CONTAINER_CPU_LIMIT:-3}"
CONTAINER_PIDS_LIMIT="${CONTAINER_PIDS_LIMIT:-2048}"
QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED="${QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED:-true}"
QUARKUS_HTTP_PROXY_ALLOW_FORWARDED="${QUARKUS_HTTP_PROXY_ALLOW_FORWARDED:-false}"
QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME="${QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME:-true}"
container_data_dir="$(echo "$DATA_VOLUME" | awk -F: '{print $2}')"
if [[ -z "$container_data_dir" ]]; then
  container_data_dir="/work/data"
fi

HOMEDIR_DATA_DIR="${HOMEDIR_DATA_DIR:-$container_data_dir}"
if [[ "${JAVA_TOOL_OPTIONS:-}" != *"-Dhomedir.data.dir="* ]]; then
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhomedir.data.dir=${HOMEDIR_DATA_DIR}"
fi
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS#"${JAVA_TOOL_OPTIONS%%[![:space:]]*}"}"

log() {
  echo "$(date -Iseconds): $*" >> "$LOGFILE"
}

fail() {
  log "ERROR: $*"
  echo "ERROR: $*" >&2
  exit 1
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

validate_runtime_baseline() {
  [[ -n "${APP_PUBLIC_URL:-}" ]] || fail "APP_PUBLIC_URL is required and must be https://<domain>"
  if [[ ! "${APP_PUBLIC_URL}" =~ ^https:// ]]; then
    fail "APP_PUBLIC_URL must use https scheme (current: ${APP_PUBLIC_URL})"
  fi
  if [[ "${APP_PUBLIC_URL}" =~ localhost|127\.0\.0\.1 ]]; then
    fail "APP_PUBLIC_URL must not point to localhost/127.0.0.1 in production (current: ${APP_PUBLIC_URL})"
  fi
}

file_env_overrides() {
  local file_var key file_path
  local -a args
  args=()
  while IFS='=' read -r file_var _; do
    [[ "${file_var}" =~ ^[A-Za-z_][A-Za-z0-9_]*_FILE$ ]] || continue
    key="${file_var%_FILE}"
    file_path="${!file_var:-}"
    [[ -n "${file_path}" ]] || continue
    [[ -f "${file_path}" ]] || fail "${file_var} points to missing file: ${file_path}"
    args+=(--env "${key}=${!key:-}")
  done < <(env)
  printf '%s\0' "${args[@]}"
}

prepare_community_storage() {
  local base="${HOMEDIR_DATA_DIR%/}/community"
  local content_dir="${base}/content"
  local submissions_dir="${base}/submissions"

  mkdir -p "$content_dir" "$submissions_dir"

  # Runtime user inside container runs with gid 0 (root group). Keep dirs group-writable
  # so moderation approval can publish curated YAML files reliably across deploys.
  chgrp -R 0 "$base" >/dev/null 2>&1 || true
  chmod -R g+rwX "$content_dir" "$submissions_dir" >/dev/null 2>&1 || true
  find "$base" -maxdepth 1 -type d -exec chmod 2775 {} + >/dev/null 2>&1 || true
  chmod 2775 "$content_dir" "$submissions_dir" >/dev/null 2>&1 || true

  if [[ -f "${submissions_dir}/pending.json" ]]; then
    chmod g+rw "${submissions_dir}/pending.json" >/dev/null 2>&1 || true
  fi

  log "community storage prepared content_dir=${content_dir} submissions_dir=${submissions_dir}"
}

if ! mkdir "$LOCKDIR" 2>/dev/null; then
  log "another update is already running; skipping tag=${TAG}"
  notify_alert "WARN" \
    "HomeDir update skipped (${DEPLOY_TRIGGER})" \
    "Another update is already running" \
    "tag=${TAG}"
  exit 0
fi
cleanup_lock() {
  rmdir "$LOCKDIR" 2>/dev/null || true
}
trap cleanup_lock EXIT

start_container() {
  local image="$1"
  local -a file_env_args
  systemctl stop homedir-podman-run.scope >/dev/null 2>&1 || true
  systemctl reset-failed homedir-podman-run.scope >/dev/null 2>&1 || true

  env_args=()
  [[ -f "$ENV_FILE" ]] && env_args+=(--env-file "$ENV_FILE")
  mapfile -d '' -t file_env_args < <(file_env_overrides)
  resource_args=()
  [[ -n "${CONTAINER_MEMORY_LIMIT:-}" ]] && resource_args+=(--memory "${CONTAINER_MEMORY_LIMIT}")
  [[ -n "${CONTAINER_CPU_LIMIT:-}" ]] && resource_args+=(--cpus "${CONTAINER_CPU_LIMIT}")
  [[ -n "${CONTAINER_PIDS_LIMIT:-}" ]] && resource_args+=(--pids-limit "${CONTAINER_PIDS_LIMIT}")

  podman run -d --name "$CONTAINER" --restart=always \
    -p "${HOST_PORT}:${CONTAINER_PORT}" \
    "${env_args[@]}" \
    "${file_env_args[@]}" \
    "${resource_args[@]}" \
    -e "HOMEDIR_DATA_DIR=${HOMEDIR_DATA_DIR}" \
    -e "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" \
    -e "APP_PUBLIC_URL=${APP_PUBLIC_URL}" \
    -e "QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED=true" \
    -e "QUARKUS_HTTP_PROXY_ALLOW_FORWARDED=false" \
    -e "QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=true" \
    -v "$DATA_VOLUME" \
    "$image" >>"$LOGFILE" 2>&1
}

log "starting update for tag=${TAG}"
validate_runtime_baseline
if [[ "$RAW_TAG" != "$TAG" ]]; then
  log "normalized incoming tag raw=${RAW_TAG} normalized=${TAG}"
fi
log "runtime data dir configured as ${HOMEDIR_DATA_DIR} (volume=${DATA_VOLUME})"
log "runtime limits memory=${CONTAINER_MEMORY_LIMIT:-none} cpus=${CONTAINER_CPU_LIMIT:-none} pids=${CONTAINER_PIDS_LIMIT:-none}"
prepare_community_storage

prev_image=""
current_digest=""
if podman container exists "$CONTAINER" >/dev/null 2>&1; then
  prev_image=$(podman inspect -f '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || true)
  log "previous container image=${prev_image}"
  current_image_id=$(podman inspect -f '{{.Image}}' "$CONTAINER" 2>/dev/null || true)
  if [[ -n "${current_image_id:-}" ]]; then
    current_digest=$(podman image inspect "$current_image_id" --format '{{.Digest}}' 2>/dev/null || true)
    [[ -n "${current_digest:-}" ]] && log "current container digest=${current_digest}"
  fi
fi

if ! podman pull "$IMAGE" >>"$LOGFILE" 2>&1; then
  log "failed to pull ${IMAGE}"
  notify_alert "FAIL" \
    "HomeDir deploy failed (${DEPLOY_TRIGGER})" \
    "Image pull failed for ${IMAGE}" \
    "tag=${TAG}"
  exit 1
fi

new_digest=$(podman image inspect "$IMAGE" --format '{{.Digest}}')
log "pulled ${IMAGE} digest=${new_digest}"

if [[ -n "${current_digest:-}" && -n "${new_digest:-}" && "${current_digest}" == "${new_digest}" ]]; then
  log "current digest already matches ${new_digest}; skipping restart"
  notify_alert "WARN" \
    "HomeDir deploy skipped (${DEPLOY_TRIGGER})" \
    "Current container already matches target digest" \
    "tag=${TAG} digest=${new_digest}"
  exit 0
fi

if podman container exists "$CONTAINER" >/dev/null 2>&1; then
  log "removing existing container ${CONTAINER}"
  podman rm -f "$CONTAINER" >>"$LOGFILE" 2>&1 || true
fi

if start_container "$IMAGE"; then
  log "deployed ${IMAGE} (digest ${new_digest})"
  notify_alert "RECOVERY" \
    "HomeDir deployed (${DEPLOY_TRIGGER})" \
    "Service running with ${IMAGE}" \
    "tag=${TAG} digest=${new_digest}"
  exit 0
fi

log "deploy failed for ${IMAGE}"
notify_alert "FAIL" \
  "HomeDir deploy failed (${DEPLOY_TRIGGER})" \
  "Container restart failed for ${IMAGE}" \
  "tag=${TAG}"

if [[ -n "$prev_image" ]]; then
  log "attempting rollback to ${prev_image}"
  podman rm -f "$CONTAINER" >>"$LOGFILE" 2>&1 || true
  if start_container "$prev_image"; then
    log "rollback succeeded using ${prev_image}"
    notify_alert "RECOVERY" \
      "HomeDir rollback succeeded (${DEPLOY_TRIGGER})" \
      "Recovered service using previous image" \
      "previous=${prev_image}"
    exit 0
  else
    log "rollback failed for ${prev_image}"
    notify_alert "FAIL" \
      "HomeDir rollback failed (${DEPLOY_TRIGGER})" \
      "Rollback container could not start" \
      "previous=${prev_image}"
  fi
fi

exit 1
