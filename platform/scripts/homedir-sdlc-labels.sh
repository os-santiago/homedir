#!/usr/bin/env bash
# Ensure GitHub labels required by the HomeDir autonomous SDLC exist.

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
QUEUE_LABEL="${HOMEDIR_SDLC_QUEUE_LABEL:-scc-queued}"
REJECTED_LABEL="${HOMEDIR_SDLC_REJECTED_LABEL:-scc-rejected}"
UNAUTHORIZED_LABEL="${HOMEDIR_SDLC_UNAUTHORIZED_LABEL:-scc-rejected:unauthorized-labeler}"
ADMISSION_REVIEW_LABEL="${HOMEDIR_SDLC_ADMISSION_REVIEW_LABEL:-scc-admission-review}"
ACCEPTED_LABEL="${HOMEDIR_SDLC_ACCEPTED_LABEL:-scc-accepted}"
RUNNING_LABEL="${HOMEDIR_SDLC_RUNNING_LABEL:-scc-running}"
PR_LABEL="${HOMEDIR_SDLC_PR_LABEL:-scc-pr-open}"
PR_TRACK_LABEL="${HOMEDIR_SDLC_PR_TRACK_LABEL:-ai-sdlc-track}"
PR_ASSIST_LABEL="${HOMEDIR_SDLC_PR_ASSIST_LABEL:-ai-sdlc-assist}"
WAITING_CHECKS_LABEL="${HOMEDIR_SDLC_WAITING_CHECKS_LABEL:-scc-waiting-checks}"
FAILING_CHECKS_LABEL="${HOMEDIR_SDLC_FAILING_CHECKS_LABEL:-scc-failing-checks}"
UNDER_REVIEW_LABEL="${HOMEDIR_SDLC_UNDER_REVIEW_LABEL:-scc-under-review}"
COVERAGE_GAP_LABEL="${HOMEDIR_SDLC_COVERAGE_GAP_LABEL:-scc-coverage-gap}"
APPROVED_LABEL="${HOMEDIR_SDLC_APPROVED_LABEL:-scc-approved}"
FAILED_LABEL="${HOMEDIR_SDLC_FAILED_LABEL:-scc-failed}"
NEEDS_HUMAN_LABEL="${HOMEDIR_SDLC_NEEDS_HUMAN_LABEL:-needs-human}"
LEGAL_REVIEW_LABEL="${HOMEDIR_SDLC_LEGAL_REVIEW_LABEL:-scc-legal-review}"
MERGED_LABEL="${HOMEDIR_SDLC_MERGED_LABEL:-scc-merged}"
GH_BIN="${GH_BIN:-${HOME}/.local/bin/gh}"

if [[ ! -x "${GH_BIN}" ]]; then
  GH_BIN="$(command -v gh)"
fi

ensure_label() {
  local name="$1"
  local color="$2"
  local description="$3"

  "${GH_BIN}" label create "${name}" \
    --repo "${REPO}" \
    --color "${color}" \
    --description "${description}" \
    --force >/dev/null
}

ensure_label "${TRIGGER_LABEL}" "0E8A16" "Human request for authorized AI SDLC admission"
ensure_label "${QUEUE_LABEL}" "0E8A16" "Authorized AI SDLC queue entry"
ensure_label "${REJECTED_LABEL}" "D93F0B" "Rejected from the AI SDLC queue"
ensure_label "${UNAUTHORIZED_LABEL}" "D93F0B" "AI SDLC admission rejected because labeler is not authorized"
ensure_label "${ADMISSION_REVIEW_LABEL}" "FBCA04" "AI SDLC is reviewing initial issue admission criteria"
ensure_label "${ACCEPTED_LABEL}" "0E8A16" "AI SDLC initial issue admission criteria passed"
ensure_label "${RUNNING_LABEL}" "FBCA04" "Autonomous SCC worker has claimed this issue"
ensure_label "${PR_LABEL}" "1D76DB" "Autonomous SCC worker opened a pull request"
ensure_label "${PR_TRACK_LABEL}" "C5DEF5" "AI SDLC is tracking this pull request"
ensure_label "${PR_ASSIST_LABEL}" "1D76DB" "AI SDLC may assist this collaborator pull request when safe"
ensure_label "${WAITING_CHECKS_LABEL}" "C5DEF5" "Autonomous SDLC PR is waiting for checks or review"
ensure_label "${FAILING_CHECKS_LABEL}" "D73A4A" "Autonomous SDLC PR has failing checks"
ensure_label "${UNDER_REVIEW_LABEL}" "BFDADC" "Autonomous SDLC PR is under automated review remediation"
ensure_label "${COVERAGE_GAP_LABEL}" "FBCA04" "Autonomous SDLC PR lacks issue coverage evidence"
ensure_label "${APPROVED_LABEL}" "0E8A16" "Autonomous SDLC PR passed checks and review feedback"
ensure_label "${FAILED_LABEL}" "D73A4A" "Autonomous SCC worker failed and needs inspection"
ensure_label "${NEEDS_HUMAN_LABEL}" "B60205" "Human decision or intervention required"
ensure_label "${LEGAL_REVIEW_LABEL}" "B60205" "Legal/compliance review required before implementation"
ensure_label "${MERGED_LABEL}" "5319E7" "Autonomous SCC PR merged or completed"

echo "Autonomous SDLC labels ensured for ${REPO}"
