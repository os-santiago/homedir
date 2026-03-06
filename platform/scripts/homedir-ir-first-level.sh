#!/usr/bin/env bash
set -euo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
  set +a
fi

INCIDENT_LOCK_FILE="${INCIDENT_LOCK_FILE:-/etc/homedir.incident.lock}"
INCIDENT_LOG_DIR="${INCIDENT_LOG_DIR:-/var/log/homedir-incident}"
INCIDENT_HEALTH_PATH="${INCIDENT_HEALTH_PATH:-/q/health}"
INCIDENT_HEALTH_TIMEOUT_SECONDS="${INCIDENT_HEALTH_TIMEOUT_SECONDS:-180}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_NAME="${CONTAINER_NAME:-homedir}"
UPDATE_SCRIPT="${UPDATE_SCRIPT:-/usr/local/bin/homedir-update.sh}"
ALERT_SCRIPT="${ALERT_SCRIPT:-/usr/local/bin/homedir-discord-alert.sh}"

usage() {
  cat <<'EOF'
Usage:
  homedir-ir-first-level.sh <command> [args]

Commands:
  status
      Show incident lock status, container state, and health endpoint result.

  snapshot
      Capture first-level diagnostics under /var/log/homedir-incident/<timestamp>.

  shield-on
      Enable emergency shield (maintenance mode) using incident lock + nginx reload.

  shield-off
      Disable emergency shield and restore public traffic.

  restart
      Restart running HomeDir container and wait for health endpoint.

  deploy-tag <tag>
      Deploy explicit image tag through homedir-update.sh and wait for health endpoint.

  recover <tag>
      Combined first-level recovery:
      1) snapshot
      2) shield-on
      3) deploy-tag <tag>
      4) shield-off

Examples:
  homedir-ir-first-level.sh status
  homedir-ir-first-level.sh shield-on
  homedir-ir-first-level.sh snapshot
  homedir-ir-first-level.sh recover v3.501.0
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
  command -v "$cmd" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

notify_alert() {
  local severity="$1"
  local title="$2"
  local message="$3"
  local details="${4:-}"
  if [[ -x "${ALERT_SCRIPT}" ]]; then
    "${ALERT_SCRIPT}" "${severity}" "${title}" "${message}" "${details}" >/dev/null 2>&1 || true
  fi
}

reload_nginx() {
  need_cmd systemctl
  if ! systemctl reload nginx; then
    fail "failed to reload nginx"
  fi
}

health_url() {
  echo "http://127.0.0.1:${HOST_PORT}${INCIDENT_HEALTH_PATH}"
}

wait_for_health() {
  local timeout="${INCIDENT_HEALTH_TIMEOUT_SECONDS}"
  local url
  url="$(health_url)"
  local attempts=$(( timeout / 5 ))
  (( attempts < 1 )) && attempts=1
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      log "healthcheck passed: ${url}"
      return 0
    fi
    sleep 5
  done
  return 1
}

capture_cmd() {
  local out_file="$1"
  shift
  {
    echo "### command: $*"
    "$@" 2>&1 || true
    echo
  } >> "${out_file}"
}

cmd_status() {
  local url
  url="$(health_url)"
  if [[ -f "${INCIDENT_LOCK_FILE}" ]]; then
    echo "incident_shield=ON (${INCIDENT_LOCK_FILE})"
  else
    echo "incident_shield=OFF"
  fi

  if podman container exists "${CONTAINER_NAME}" >/dev/null 2>&1; then
    podman ps --filter "name=${CONTAINER_NAME}" --format "container={{.Names}} image={{.Image}} status={{.Status}}"
  else
    echo "container=${CONTAINER_NAME} not found"
  fi

  if curl -fsS "${url}" >/dev/null 2>&1; then
    echo "health=OK (${url})"
  else
    echo "health=FAIL (${url})"
    return 1
  fi
}

cmd_snapshot() {
  need_cmd podman
  need_cmd journalctl
  need_cmd ss
  need_cmd df
  need_cmd free
  need_cmd uptime

  local ts
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  local dir="${INCIDENT_LOG_DIR}/${ts}"
  local report="${dir}/incident-report.txt"
  mkdir -p "${dir}"

  {
    echo "incident_ts=${ts}"
    echo "host=$(hostname)"
    echo "shield_lock_file=${INCIDENT_LOCK_FILE}"
    echo
  } > "${report}"

  capture_cmd "${report}" date -u
  capture_cmd "${report}" uptime
  capture_cmd "${report}" free -m
  capture_cmd "${report}" df -h
  capture_cmd "${report}" ss -s
  capture_cmd "${report}" podman ps --no-trunc
  capture_cmd "${report}" podman stats --no-stream
  capture_cmd "${report}" journalctl -u nginx --since "-15 min" --no-pager
  capture_cmd "${report}" journalctl -u homedir-auto-deploy.service --since "-30 min" --no-pager
  capture_cmd "${report}" journalctl -u homedir-webhook.service --since "-30 min" --no-pager

  for file in /var/log/homedir-update.log /var/log/homedir-auto-deploy.log /var/log/homedir-webhook.log /var/log/nginx/access.log /var/log/nginx/error.log; do
    if [[ -f "${file}" ]]; then
      tail -n 500 "${file}" > "${dir}/$(basename "${file}").tail" || true
    fi
  done

  echo "snapshot_dir=${dir}"
  notify_alert "WARN" "HomeDir incident snapshot captured" "First-level snapshot generated" "dir=${dir}"
}

cmd_shield_on() {
  touch "${INCIDENT_LOCK_FILE}"
  chmod 600 "${INCIDENT_LOCK_FILE}" || true
  reload_nginx
  log "incident shield enabled"
  notify_alert "WARN" "HomeDir incident shield enabled" "Public traffic switched to maintenance mode"
}

cmd_shield_off() {
  rm -f "${INCIDENT_LOCK_FILE}"
  reload_nginx
  log "incident shield disabled"
  notify_alert "RECOVERY" "HomeDir incident shield disabled" "Public traffic restored"
}

cmd_restart() {
  need_cmd podman
  podman restart "${CONTAINER_NAME}" >/dev/null
  if ! wait_for_health; then
    fail "healthcheck failed after container restart"
  fi
  notify_alert "RECOVERY" "HomeDir container restart succeeded" "Service recovered after restart"
}

cmd_deploy_tag() {
  local tag="${1:-}"
  [[ -n "${tag}" ]] || fail "deploy-tag requires <tag>"
  [[ -x "${UPDATE_SCRIPT}" ]] || fail "update script not executable: ${UPDATE_SCRIPT}"
  DEPLOY_TRIGGER=incident "${UPDATE_SCRIPT}" "${tag}"
  if ! wait_for_health; then
    fail "healthcheck failed after deploying tag ${tag}"
  fi
  notify_alert "RECOVERY" "HomeDir incident deploy succeeded" "Service running after incident deploy" "tag=${tag}"
}

cmd_recover() {
  local tag="${1:-}"
  [[ -n "${tag}" ]] || fail "recover requires <tag>"
  cmd_snapshot
  cmd_shield_on
  if ! cmd_deploy_tag "${tag}"; then
    notify_alert "FAIL" "HomeDir first-level recover failed" "Recovery failed during deploy-tag" "tag=${tag}"
    fail "recover failed for tag ${tag}"
  fi
  cmd_shield_off
  log "first-level recovery completed with tag ${tag}"
}

main() {
  local cmd="${1:-}"
  case "${cmd}" in
    status)
      shift
      cmd_status "$@"
      ;;
    snapshot)
      shift
      cmd_snapshot "$@"
      ;;
    shield-on)
      shift
      cmd_shield_on "$@"
      ;;
    shield-off)
      shift
      cmd_shield_off "$@"
      ;;
    restart)
      shift
      cmd_restart "$@"
      ;;
    deploy-tag)
      shift
      cmd_deploy_tag "$@"
      ;;
    recover)
      shift
      cmd_recover "$@"
      ;;
    -h|--help|help|"")
      usage
      ;;
    *)
      fail "unknown command: ${cmd}"
      ;;
  esac
}

need_cmd curl
main "$@"

