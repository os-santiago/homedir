#!/usr/bin/env bash
set -euo pipefail

# Optional: override with ENV_FILE=/path/to/envfile
ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

TAG="${1:-${DEPLOY_TAG:-}}"
if [[ -z "$TAG" ]]; then
  echo "usage: homedir-update.sh <tag> (or set DEPLOY_TAG in env)" >&2
  exit 1
fi

REPO="${IMAGE_REPO:-quay.io/sergio_canales_e/homedir}"
IMAGE="${REPO}:${TAG}"
LOGFILE="${LOGFILE:-/var/log/homedir-update.log}"
CONTAINER="${CONTAINER_NAME:-homedir}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
DATA_VOLUME="${DATA_VOLUME:-/work/data:/work/data:Z}"
LOCKFILE="${LOCKFILE:-/var/lock/homedir-update.lock}"
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

exec 9>"$LOCKFILE"
if ! flock -n 9; then
  log "another update is already running; skipping tag=${TAG}"
  exit 0
fi

start_container() {
  local image="$1"
  systemctl stop homedir-podman-run.scope >/dev/null 2>&1 || true
  systemctl reset-failed homedir-podman-run.scope >/dev/null 2>&1 || true

  env_args=()
  [[ -f "$ENV_FILE" ]] && env_args+=(--env-file "$ENV_FILE")

  podman run -d --name "$CONTAINER" --restart=always \
    -p "${HOST_PORT}:${CONTAINER_PORT}" \
    "${env_args[@]}" \
    -e "HOMEDIR_DATA_DIR=${HOMEDIR_DATA_DIR}" \
    -e "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" \
    -v "$DATA_VOLUME" \
    "$image" >>"$LOGFILE" 2>&1
}

log "starting update for tag=${TAG}"
log "runtime data dir configured as ${HOMEDIR_DATA_DIR} (volume=${DATA_VOLUME})"

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
  exit 1
fi

new_digest=$(podman image inspect "$IMAGE" --format '{{.Digest}}')
log "pulled ${IMAGE} digest=${new_digest}"

if [[ -n "${current_digest:-}" && -n "${new_digest:-}" && "${current_digest}" == "${new_digest}" ]]; then
  log "current digest already matches ${new_digest}; skipping restart"
  exit 0
fi

if podman container exists "$CONTAINER" >/dev/null 2>&1; then
  log "removing existing container ${CONTAINER}"
  podman rm -f "$CONTAINER" >>"$LOGFILE" 2>&1 || true
fi

if start_container "$IMAGE"; then
  log "deployed ${IMAGE} (digest ${new_digest})"
  exit 0
fi

log "deploy failed for ${IMAGE}"

if [[ -n "$prev_image" ]]; then
  log "attempting rollback to ${prev_image}"
  podman rm -f "$CONTAINER" >>"$LOGFILE" 2>&1 || true
  if start_container "$prev_image"; then
    log "rollback succeeded using ${prev_image}"
    exit 0
  else
    log "rollback failed for ${prev_image}"
  fi
fi

exit 1
