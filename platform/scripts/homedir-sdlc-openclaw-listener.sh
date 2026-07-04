#!/usr/bin/env bash
# OpenClaw/GitHub issue-event adapter for the HomeDir autonomous SDLC worker.

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
AUTHOR="${HOMEDIR_SDLC_AUTHOR:-scanalesespinoza}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
WORKER_BIN="${HOMEDIR_SDLC_WORKER_BIN:-homedir-sdlc-worker.sh}"
LOGFILE="${HOMEDIR_SDLC_OPENCLAW_LOGFILE:-${HOMEDIR_SDLC_STATE_DIR:-/var/lib/homedir-sdlc}/logs/openclaw-listener.log}"

mkdir -p "$(dirname "${LOGFILE}")"

log() {
  printf '%s [homedir-sdlc-openclaw] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "${LOGFILE}" >&2
}

payload_file="${1:-}"
tmp_payload=""
if [[ -z "${payload_file}" ]]; then
  tmp_payload="$(mktemp)"
  cat >"${tmp_payload}"
  payload_file="${tmp_payload}"
fi

cleanup() {
  if [[ -n "${tmp_payload}" ]]; then
    rm -f "${tmp_payload}"
  fi
}
trap cleanup EXIT

if ! jq empty "${payload_file}" >/dev/null 2>&1; then
  log "ignored invalid JSON payload"
  exit 0
fi

action="$(jq -r '.action // ""' "${payload_file}")"
issue_state="$(jq -r '.issue.state // ""' "${payload_file}")"
issue_author="$(jq -r '.issue.user.login // ""' "${payload_file}")"
issue_number="$(jq -r '.issue.number // ""' "${payload_file}")"
repository="$(jq -r '.repository.full_name // ""' "${payload_file}")"
event_label="$(jq -r '.label.name // ""' "${payload_file}")"
has_trigger="$(jq -r --arg label "${TRIGGER_LABEL}" '[.issue.labels[]?.name] | index($label) != null' "${payload_file}")"

case "${action}" in
  opened|edited|reopened|labeled)
    ;;
  *)
    log "ignored action=${action}"
    exit 0
    ;;
esac

if [[ "${repository}" != "${REPO}" ]]; then
  log "ignored repository=${repository}"
  exit 0
fi

if [[ "${issue_state}" != "open" || "${issue_author}" != "${AUTHOR}" ]]; then
  log "ignored issue #${issue_number}: state=${issue_state} author=${issue_author}"
  exit 0
fi

if [[ "${action}" == "labeled" && "${event_label}" != "${TRIGGER_LABEL}" ]]; then
  log "ignored issue #${issue_number}: labeled ${event_label}"
  exit 0
fi

if [[ "${has_trigger}" != "true" ]]; then
  log "ignored issue #${issue_number}: missing ${TRIGGER_LABEL}"
  exit 0
fi

log "triggering worker for issue #${issue_number}"
if systemctl --user start homedir-sdlc-worker.service >/dev/null 2>&1; then
  log "started homedir-sdlc-worker.service"
else
  nohup "${WORKER_BIN}" >>"${LOGFILE}" 2>&1 &
  log "started worker directly pid=$!"
fi
