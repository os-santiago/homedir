#!/usr/bin/env bash
# JSON status probe for the HomeDir autonomous SDLC runner.

set -euo pipefail

DEFAULT_ENV_FILE="/etc/homedir-sdlc.env"
if [[ ! -f "${DEFAULT_ENV_FILE}" && -f "${HOME:-/home/homedir-sdlc}/.config/homedir-sdlc/env" ]]; then
  DEFAULT_ENV_FILE="${HOME:-/home/homedir-sdlc}/.config/homedir-sdlc/env"
fi
ENV_FILE="${HOMEDIR_SDLC_ENV_FILE:-${DEFAULT_ENV_FILE}}"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi
if [[ -z "${XDG_RUNTIME_DIR:-}" && -d "/run/user/$(id -u)" ]]; then
  export XDG_RUNTIME_DIR="/run/user/$(id -u)"
fi
if [[ -n "${XDG_RUNTIME_DIR:-}" && -z "${DBUS_SESSION_BUS_ADDRESS:-}" && -S "${XDG_RUNTIME_DIR}/bus" ]]; then
  export DBUS_SESSION_BUS_ADDRESS="unix:path=${XDG_RUNTIME_DIR}/bus"
fi

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-/var/lib/homedir-sdlc}"
HEARTBEAT_FILE="${HOMEDIR_SDLC_HEARTBEAT_FILE:-${STATE_DIR}/heartbeat.json}"
MAX_AGE_SECONDS="${HOMEDIR_SDLC_HEARTBEAT_MAX_AGE_SECONDS:-900}"

now_epoch="$(date -u +%s)"
heartbeat_status="missing"
heartbeat_detail="heartbeat file is missing"
heartbeat_updated_at=""
heartbeat_age_seconds=""
healthy=true

if [[ -r "${HEARTBEAT_FILE}" ]]; then
  heartbeat_status="$(jq -r '.status // "unknown"' "${HEARTBEAT_FILE}")"
  heartbeat_detail="$(jq -r '.detail // ""' "${HEARTBEAT_FILE}")"
  heartbeat_updated_at="$(jq -r '.updated_at // ""' "${HEARTBEAT_FILE}")"
  if [[ -n "${heartbeat_updated_at}" ]]; then
    heartbeat_epoch="$(date -u -d "${heartbeat_updated_at}" +%s 2>/dev/null || echo 0)"
    if [[ "${heartbeat_epoch}" -gt 0 ]]; then
      heartbeat_age_seconds="$((now_epoch - heartbeat_epoch))"
      if [[ "${heartbeat_age_seconds}" -gt "${MAX_AGE_SECONDS}" ]]; then
        healthy=false
        heartbeat_detail="stale heartbeat: ${heartbeat_age_seconds}s old"
      fi
    fi
  fi
else
  healthy=false
fi

service_state="unknown"
timer_state="unknown"
if systemctl --user is-active homedir-sdlc-worker.timer >/dev/null 2>&1 \
  || systemctl --user is-active homedir-sdlc-worker.service >/dev/null 2>&1; then
  service_state="$(systemctl --user is-active homedir-sdlc-worker.service 2>/dev/null || true)"
  timer_state="$(systemctl --user is-active homedir-sdlc-worker.timer 2>/dev/null || true)"
  if [[ "${timer_state}" != "active" ]]; then
    healthy=false
  fi
fi

eligible_issues_json="[]"
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  eligible_issues_json="$(gh issue list \
    --repo "${REPO}" \
    --state open \
    --label "${TRIGGER_LABEL}" \
    --limit 20 \
    --json number,title,url,labels 2>/dev/null || echo "[]")"
fi

jq -n \
  --arg repo "${REPO}" \
  --argjson healthy "${healthy}" \
  --arg heartbeat_status "${heartbeat_status}" \
  --arg heartbeat_detail "${heartbeat_detail}" \
  --arg heartbeat_updated_at "${heartbeat_updated_at}" \
  --arg heartbeat_age_seconds "${heartbeat_age_seconds}" \
  --arg service_state "${service_state}" \
  --arg timer_state "${timer_state}" \
  --argjson eligible_issues "${eligible_issues_json}" \
  '{
    repo: $repo,
    healthy: $healthy,
    heartbeat: {
      status: $heartbeat_status,
      detail: $heartbeat_detail,
      updated_at: $heartbeat_updated_at,
      age_seconds: (if $heartbeat_age_seconds == "" then null else ($heartbeat_age_seconds|tonumber) end)
    },
    systemd: {
      service: $service_state,
      timer: $timer_state
    },
    eligible_issues: $eligible_issues
  }'

if [[ "${healthy}" != "true" ]]; then
  exit 1
fi
