#!/usr/bin/env bash
# OpenClaw/GitHub issue-event adapter for the HomeDir autonomous SDLC worker.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_LIB="${HOMEDIR_ENV_LIB:-${SCRIPT_DIR}/homedir-env-lib.sh}"
if [[ -r "${ENV_LIB}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_LIB}"
  homedir_sdlc_runtime_load
fi

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
QUEUE_LABEL="${HOMEDIR_SDLC_QUEUE_LABEL:-scc-queued}"
AUTHORIZED_LABELERS="${HOMEDIR_SDLC_AUTHORIZED_LABELERS:-scanalesespinoza}"
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
issue_number="$(jq -r '.issue.number // ""' "${payload_file}")"
repository="$(jq -r '.repository.full_name // ""' "${payload_file}")"
event_label="$(jq -r '.label.name // ""' "${payload_file}")"
labeler="$(jq -r '.sender.login // ""' "${payload_file}")"
has_trigger="$(jq -r --arg label "${TRIGGER_LABEL}" '[.issue.labels[]?.name] | index($label) != null' "${payload_file}")"

is_authorized_labeler() {
  local login="$1"
  local item
  IFS=',' read -r -a labelers <<<"${AUTHORIZED_LABELERS}"
  for item in "${labelers[@]}"; do
    item="${item//[[:space:]]/}"
    if [[ -n "${item}" && "${login}" == "${item}" ]]; then
      return 0
    fi
  done
  return 1
}

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

if [[ "${issue_state}" != "open" ]]; then
  log "ignored issue #${issue_number}: state=${issue_state}"
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

if ! is_authorized_labeler "${labeler}"; then
  log "ignored issue #${issue_number}: labeler ${labeler:-unknown} is not authorized for ${TRIGGER_LABEL}"
  exit 0
fi

log "triggering worker for issue #${issue_number}; admission label=${QUEUE_LABEL} labeler=${labeler}"
if systemctl --user start --no-block homedir-sdlc-worker.service >/dev/null 2>&1; then
  log "started homedir-sdlc-worker.service"
else
  nohup "${WORKER_BIN}" >>"${LOGFILE}" 2>&1 &
  log "started worker directly pid=$!"
fi
