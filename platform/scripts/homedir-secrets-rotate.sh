#!/usr/bin/env bash
set -euo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
DRY_RUN="false"
RESTART_SERVICES="false"
BACKUP_PATH=""
declare -a KEYS

usage() {
  cat <<'EOF'
Usage:
  homedir-secrets-rotate.sh [options]

Description:
  Rotates internal HomeDir runtime secrets with backup and optional service restart.
  Intended for periodic rotation without changing external provider credentials.

Options:
  --env-file <path>         Environment file path (default: /etc/homedir.env)
  --key <NAME>              Secret key to rotate (repeatable). Defaults:
                            WEBHOOK_SHARED_SECRET
                            WEBHOOK_STATUS_TOKEN
                            SESSION_KEY
                            NOTIFICATIONS_USER_HASH_SALT
  --restart-services        Restart webhook and recreate app container after rotation
  --dry-run                 Print actions without writing changes
  -h, --help                Show this help

Examples:
  homedir-secrets-rotate.sh
  homedir-secrets-rotate.sh --restart-services
  homedir-secrets-rotate.sh --key SESSION_KEY --key WEBHOOK_SHARED_SECRET
EOF
}

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

need_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

run_cmd() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: $*"
    return 0
  fi
  "$@"
}

env_lookup() {
  local key="$1"
  awk -F= -v k="${key}" '$1 == k {sub(/^[^=]*=/, "", $0); print $0; exit}' "${ENV_FILE}"
}

set_env_value() {
  local key="$1"
  local value="$2"
  local escaped
  escaped="$(printf '%s' "${value}" | sed 's/[\\/&]/\\&/g')"
  if grep -qE "^${key}=" "${ENV_FILE}"; then
    run_cmd sed -i "s|^${key}=.*$|${key}=${escaped}|" "${ENV_FILE}"
  else
    if [[ "${DRY_RUN}" == "true" ]]; then
      echo "DRY-RUN: append ${key}=<redacted> to ${ENV_FILE}"
    else
      printf '%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}"
    fi
  fi
}

write_secret_target() {
  local key="$1"
  local value="$2"
  local file_key="${key}_FILE"
  local file_path
  file_path="$(env_lookup "${file_key}" || true)"
  if [[ -n "${file_path}" ]]; then
    run_cmd install -d -m 0700 "$(dirname "${file_path}")"
    if [[ "${DRY_RUN}" == "true" ]]; then
      echo "DRY-RUN: write ${file_path} (redacted)"
    else
      printf '%s' "${value}" > "${file_path}"
    fi
    run_cmd chown root:root "${file_path}"
    run_cmd chmod 600 "${file_path}"
    log "rotated ${key} via ${file_key}"
    return 0
  fi

  set_env_value "${key}" "${value}"
  log "rotated ${key} in ${ENV_FILE}"
}

generate_secret() {
  local key="$1"
  case "${key}" in
    SESSION_KEY)
      openssl rand -base64 48 | tr -d '\n'
      ;;
    NOTIFICATIONS_USER_HASH_SALT|WEBHOOK_SHARED_SECRET|WEBHOOK_STATUS_TOKEN)
      openssl rand -hex 32
      ;;
    *)
      openssl rand -base64 32 | tr -d '\n'
      ;;
  esac
}

restart_runtime() {
  local image tag
  if systemctl list-unit-files --no-legend homedir-webhook.service 2>/dev/null | grep -q homedir-webhook.service; then
    run_cmd systemctl restart homedir-webhook.service
  fi

  if ! command -v podman >/dev/null 2>&1; then
    log "podman not available; skipping app container restart"
    return 0
  fi

  image="$(podman ps --filter name=homedir --format '{{.Image}}' | head -n 1 || true)"
  if [[ -z "${image}" ]]; then
    log "homedir container not running; skipping app restart"
    return 0
  fi
  tag="${image##*:}"
  run_cmd /usr/local/bin/homedir-update.sh "${tag}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --key)
      KEYS+=("${2:-}")
      shift 2
      ;;
    --restart-services)
      RESTART_SERVICES="true"
      shift
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ "${EUID}" -eq 0 ]] || fail "run as root"
need_cmd awk
need_cmd sed
need_cmd openssl
[[ -f "${ENV_FILE}" ]] || fail "env file not found: ${ENV_FILE}"

if [[ "${#KEYS[@]}" -eq 0 ]]; then
  KEYS=(
    "WEBHOOK_SHARED_SECRET"
    "WEBHOOK_STATUS_TOKEN"
    "SESSION_KEY"
    "NOTIFICATIONS_USER_HASH_SALT"
  )
fi

BACKUP_PATH="${ENV_FILE}.bak.$(date -u +%Y%m%dT%H%M%SZ)"
run_cmd cp "${ENV_FILE}" "${BACKUP_PATH}"
run_cmd chown root:root "${BACKUP_PATH}"
run_cmd chmod 600 "${BACKUP_PATH}"

for key in "${KEYS[@]}"; do
  [[ "${key}" =~ ^[A-Z0-9_]+$ ]] || fail "invalid key format: ${key}"
  value="$(generate_secret "${key}")"
  write_secret_target "${key}" "${value}"
done

run_cmd chown root:root "${ENV_FILE}"
run_cmd chmod 600 "${ENV_FILE}"

if [[ "${RESTART_SERVICES}" == "true" ]]; then
  restart_runtime
fi

log "secret rotation complete keys=${#KEYS[@]} backup=${BACKUP_PATH} restart=${RESTART_SERVICES}"
