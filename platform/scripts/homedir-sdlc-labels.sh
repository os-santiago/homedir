#!/usr/bin/env bash
# Ensure GitHub labels required by the HomeDir autonomous SDLC exist.

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
RUNNING_LABEL="${HOMEDIR_SDLC_RUNNING_LABEL:-scc-running}"
PR_LABEL="${HOMEDIR_SDLC_PR_LABEL:-scc-pr-open}"
FAILED_LABEL="${HOMEDIR_SDLC_FAILED_LABEL:-scc-failed}"
NEEDS_HUMAN_LABEL="${HOMEDIR_SDLC_NEEDS_HUMAN_LABEL:-needs-human}"
MERGED_LABEL="${HOMEDIR_SDLC_MERGED_LABEL:-scc-merged}"
GH_BIN="${GH_BIN:-${HOME}/.local/bin/gh}"

if [[ ! -x "${GH_BIN}" ]]; then
  GH_BIN="$(command -v gh)"
fi

ensure_label() {
  local name="$1"
  local color="$2"
  local description="$3"

  if "${GH_BIN}" label view "${name}" --repo "${REPO}" >/dev/null 2>&1; then
    "${GH_BIN}" label edit "${name}" --repo "${REPO}" --color "${color}" --description "${description}" >/dev/null
  else
    "${GH_BIN}" label create "${name}" --repo "${REPO}" --color "${color}" --description "${description}" >/dev/null
  fi
}

ensure_label "${TRIGGER_LABEL}" "0E8A16" "Approved trigger for autonomous SCC implementation"
ensure_label "${RUNNING_LABEL}" "FBCA04" "Autonomous SCC worker has claimed this issue"
ensure_label "${PR_LABEL}" "1D76DB" "Autonomous SCC worker opened a pull request"
ensure_label "${FAILED_LABEL}" "D73A4A" "Autonomous SCC worker failed and needs inspection"
ensure_label "${NEEDS_HUMAN_LABEL}" "B60205" "Human decision or intervention required"
ensure_label "${MERGED_LABEL}" "5319E7" "Autonomous SCC PR merged or completed"

echo "Autonomous SDLC labels ensured for ${REPO}"
