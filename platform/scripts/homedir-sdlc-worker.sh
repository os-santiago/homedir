#!/usr/bin/env bash
# Issue-driven SCC worker for the HomeDir autonomous SDLC.

set -euo pipefail

ENV_FILE="${HOMEDIR_SDLC_ENV_FILE:-/etc/homedir-sdlc.env}"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
QUEUE_LABEL="${HOMEDIR_SDLC_QUEUE_LABEL:-scc-queued}"
REJECTED_LABEL="${HOMEDIR_SDLC_REJECTED_LABEL:-scc-rejected}"
UNAUTHORIZED_LABEL="${HOMEDIR_SDLC_UNAUTHORIZED_LABEL:-scc-rejected:unauthorized-labeler}"
AUTHORIZED_LABELERS="${HOMEDIR_SDLC_AUTHORIZED_LABELERS:-scanalesespinoza}"
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
MERGED_LABEL="${HOMEDIR_SDLC_MERGED_LABEL:-scc-merged}"
WORKDIR="${HOMEDIR_SDLC_WORKDIR:-/srv/homedir-sdlc/worktrees/homedir}"
STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-/var/lib/homedir-sdlc}"
LOGFILE="${HOMEDIR_SDLC_LOGFILE:-/var/log/homedir-sdlc-worker.log}"
MAX_ISSUES="${HOMEDIR_SDLC_MAX_ISSUES_PER_RUN:-1}"
ENABLE_AUTOMERGE="${HOMEDIR_SDLC_ENABLE_AUTOMERGE:-false}"
PR_REVIEW_DELAY_SECONDS="${HOMEDIR_SDLC_PR_REVIEW_DELAY_SECONDS:-600}"
VALIDATION_COMMAND="${HOMEDIR_SDLC_VALIDATION_COMMAND:-}"
GIT_USER_NAME="${HOMEDIR_SDLC_GIT_USER_NAME:-homedir-sdlc[bot]}"
GIT_USER_EMAIL="${HOMEDIR_SDLC_GIT_USER_EMAIL:-homedir-sdlc@users.noreply.github.com}"
SCC_BIN="${SCC_BIN:-/usr/local/bin/scc}"
SCC_TIMEOUT_SECONDS="${HOMEDIR_SDLC_SCC_TIMEOUT_SECONDS:-1800}"
SCC_PROFILE="${HOMEDIR_SDLC_SCC_PROFILE:-nvidia}"
SCC_CLEAR_HISTORY="${HOMEDIR_SDLC_SCC_CLEAR_HISTORY:-true}"
SCC_PERMISSIONS="${HOMEDIR_SDLC_SCC_PERMISSIONS:-unlimited}"
LOCK_FILE="${STATE_DIR}/worker.lock"
ISSUE_STATE_DIR="${STATE_DIR}/issues"
PR_STATE_DIR="${STATE_DIR}/prs"
RUN_SUMMARY_DIR="${STATE_DIR}/run-summaries"
HEARTBEAT_FILE="${HOMEDIR_SDLC_HEARTBEAT_FILE:-${STATE_DIR}/heartbeat.json}"
MAX_REMEDIATION_ATTEMPTS="${HOMEDIR_SDLC_MAX_REMEDIATION_ATTEMPTS:-5}"
ALERTS_ENABLED="${HOMEDIR_SDLC_ALERTS_ENABLED:-false}"
ALERT_WEBHOOK_URL="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL:-}"
ALERT_WEBHOOK_URL_FILE="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL_FILE:-}"
ALERT_TIMEOUT_SECONDS="${HOMEDIR_SDLC_ALERT_TIMEOUT_SECONDS:-10}"

mkdir -p "${STATE_DIR}" "${ISSUE_STATE_DIR}" "${PR_STATE_DIR}" "${RUN_SUMMARY_DIR}" "$(dirname "${LOGFILE}")"
touch "${LOGFILE}"

log() {
  printf '%s [homedir-sdlc-worker] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "${LOGFILE}" >&2
}

resolve_alert_webhook_url() {
  if [[ -n "${ALERT_WEBHOOK_URL}" ]]; then
    printf '%s' "${ALERT_WEBHOOK_URL}"
    return 0
  fi
  if [[ -n "${ALERT_WEBHOOK_URL_FILE}" && -r "${ALERT_WEBHOOK_URL_FILE}" ]]; then
    head -n1 "${ALERT_WEBHOOK_URL_FILE}"
  fi
}

alert() {
  local severity="$1"
  local title="$2"
  local message="$3"
  local webhook_url

  if [[ "${ALERTS_ENABLED}" != "true" ]]; then
    return 0
  fi

  webhook_url="$(resolve_alert_webhook_url)"
  if [[ -z "${webhook_url}" ]]; then
    log "alerts enabled but no webhook URL is configured"
    return 0
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    log "alerts enabled but python3 is unavailable"
    return 0
  fi

  ALERT_WEBHOOK_URL_RESOLVED="$webhook_url" python3 - "$severity" "$title" "$message" "$ALERT_TIMEOUT_SECONDS" <<'PY'
import json
import os
import sys
import urllib.request

severity, title, message, timeout = sys.argv[1:5]
url = os.environ["ALERT_WEBHOOK_URL_RESOLVED"]
payload = {
    "username": "homedir-sdlc",
    "embeds": [{
        "title": f"{severity}: {title}",
        "description": message,
        "color": 15105570 if severity == "WARN" else 15158332 if severity == "FAIL" else 5763719,
    }],
}
request = urllib.request.Request(
    url,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(request, timeout=int(timeout)) as response:
        response.read()
except Exception as exc:
    print(f"alert delivery failed: {exc}", file=sys.stderr)
PY
}

write_heartbeat() {
  local status="$1"
  local detail="$2"
  local tmp_file
  mkdir -p "$(dirname "${HEARTBEAT_FILE}")"
  tmp_file="$(mktemp "${HEARTBEAT_FILE}.XXXXXX")"
  if ! command -v jq >/dev/null 2>&1; then
    printf '{"repo":"%s","status":"%s","detail":"%s","updated_at":"%s"}\n' \
      "${REPO}" "${status}" "${detail}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      > "${tmp_file}"
    mv -f "${tmp_file}" "${HEARTBEAT_FILE}"
    return 0
  fi
  jq -n \
    --arg repo "${REPO}" \
    --arg status "${status}" \
    --arg detail "${detail}" \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{repo: $repo, status: $status, detail: $detail, updated_at: $updated_at}' \
    > "${tmp_file}"
  mv -f "${tmp_file}" "${HEARTBEAT_FILE}"
}

# ADEV-compliant complexity classification
classify_issue_complexity() {
  local body="$1"
  local criteria_count
  criteria_count=$(echo "$body" | grep -c '^\- \[ \]' || true)

  if [[ "${criteria_count}" -le 1 ]]; then
    echo "simple"
  elif [[ "${criteria_count}" -le 2 ]]; then
    echo "medium"
  else
    echo "complex"
  fi
}

# Get timeout based on complexity (ADEV Rule #6)
get_timeout_for_complexity() {
  local complexity="$1"
  case "${complexity}" in
    simple)  echo 300 ;;   # 5 minutes
    medium)  echo 600 ;;   # 10 minutes
    complex) echo 900 ;;   # 15 minutes
    *)       echo 600 ;;   # default 10 minutes
  esac
}

# ADEV Rule #11: Narrowest validation that proves change is sound
get_validation_command_for_changes() {
  local changed_files="$1"

  # Detect what changed and return scoped validation command
  if echo "$changed_files" | grep -q 'trending/.*\.java$'; then
    echo "cd quarkus-app && mvn test -Dtest=com.scanales.homedir.trending.*Test"
  elif echo "$changed_files" | grep -q '\.java$'; then
    # Generic Java files - run affected package tests
    echo "cd quarkus-app && mvn test"
  elif echo "$changed_files" | grep -q '\.html$'; then
    echo "cd quarkus-app && mvn test -Dtest=*ResourceTest"
  elif echo "$changed_files" | grep -q 'docs/.*\.md$'; then
    # Markdown validation only if markdownlint is available
    if command -v markdownlint >/dev/null 2>&1; then
      local md_files
      md_files=$(echo "$changed_files" | tr ' ' '\n' | grep '\.md$' | tr '\n' ' ')
      if [[ -n "${md_files}" ]]; then
        echo "markdownlint ${md_files}"
      else
        echo ""
      fi
    else
      echo ""
    fi
  elif echo "$changed_files" | grep -q 'platform/scripts/'; then
    # Shell script validation only if shellcheck is available
    if command -v shellcheck >/dev/null 2>&1; then
      local sh_files
      sh_files=$(echo "$changed_files" | tr ' ' '\n' | grep '\.sh$' | tr '\n' ' ')
      if [[ -n "${sh_files}" ]]; then
        echo "shellcheck ${sh_files}"
      else
        echo ""
      fi
    else
      echo ""
    fi
  else
    # Default: no validation (let CI handle it)
    echo ""
  fi
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 1
  fi
}

run_scc_prompt() {
  local prompt="$1"
  local issue_body="$2"
  local -a scc_args
  local dynamic_timeout="${SCC_TIMEOUT_SECONDS}"

  # Calculate dynamic timeout if issue_body provided (ADEV optimization)
  if [[ -n "${issue_body}" ]]; then
    local issue_complexity
    issue_complexity=$(classify_issue_complexity "${issue_body}")
    dynamic_timeout=$(get_timeout_for_complexity "${issue_complexity}")
    log "Issue complexity: ${issue_complexity}, timeout: ${dynamic_timeout}s"
  fi

  # Export timeout for error reporting (used by run_scc_handle_exit_code)
  export SCC_ACTUAL_TIMEOUT="${dynamic_timeout}"

  (
    cd "${WORKDIR}"
    scc_args=(chat)
    if [[ "${SCC_CLEAR_HISTORY}" == "true" ]]; then
      scc_args+=(--clear)
    fi
    if [[ -n "${SCC_PROFILE}" ]]; then
      scc_args+=(-m "${SCC_PROFILE}")
    fi
    if [[ -n "${SCC_PERMISSIONS}" ]]; then
      scc_args+=(--permissions "${SCC_PERMISSIONS}")
    fi
    # Enable throttling to avoid API rate limits (auto-detects provider-specific delays)
    scc_args+=(--throttle auto)
    scc_args+=(-yq "${prompt}")

    if command -v timeout >/dev/null 2>&1 && [[ "${dynamic_timeout}" =~ ^[0-9]+$ && "${dynamic_timeout}" -gt 0 ]]; then
      timeout "${dynamic_timeout}s" "${SCC_BIN}" "${scc_args[@]}"
    else
      log "WARNING: 'timeout' unavailable or dynamic_timeout invalid (${dynamic_timeout}); running SCC without timeout enforcement"
      "${SCC_BIN}" "${scc_args[@]}"
    fi
  ) 2>&1 | tee -a "${LOGFILE}"
  return "${PIPESTATUS[0]}"
}

run_scc_handle_exit_code() {
  local rc=$1
  local context=$2
  local actual_timeout="${SCC_ACTUAL_TIMEOUT:-${SCC_TIMEOUT_SECONDS}}"
  if [[ "${rc}" -eq 124 ]]; then
    log "SCC ${context} timed out after ${actual_timeout}s (exit code 124)"
  else
    log "SCC ${context} exited non-zero (${rc})"
  fi
  return "${rc}"
}

run_scc_with_timeout_handling() {
  local prompt="$1"
  local issue_body="${2:-}"  # Optional: empty string if not provided
  local scc_rc
  if run_scc_prompt "${prompt}" "${issue_body}"; then
    return 0
  fi
  scc_rc=$?
  run_scc_handle_exit_code "${scc_rc}" "remediation"
  return "${scc_rc}"
}

run_scc_checked() {
  local prompt="$1"
  local issue_body="${2:-}"  # Optional: empty string if not provided
  local rc

  if run_scc_prompt "${prompt}" "${issue_body}"; then
    return 0
  else
    rc=$?
    run_scc_handle_exit_code "${rc}" "initial"
    return "${rc}"
  fi
}

require_gh_auth() {
  if ! gh auth status >/dev/null 2>&1; then
    log "GitHub CLI is not authenticated on this server; configure gh auth or GH_TOKEN before running autonomous SDLC"
    exit 0
  fi
}

safe_slug() {
  printf '%s' "$1" \
    | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' \
    | cut -c1-48
}

issue_has_label() {
  local labels_json="$1"
  local wanted="$2"
  jq -e --arg wanted "${wanted}" 'index($wanted) != null' >/dev/null <<<"${labels_json}"
}

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

add_label() {
  local issue="$1"
  local label="$2"
  gh issue edit "${issue}" --repo "${REPO}" --add-label "${label}" >/dev/null
}

remove_label() {
  local issue="$1"
  local label="$2"
  gh issue edit "${issue}" --repo "${REPO}" --remove-label "${label}" >/dev/null 2>&1 || true
}

set_flow_labels() {
  local issue="$1"
  shift
  local wanted label
  wanted=" $* "

  for label in \
    "${QUEUE_LABEL}" \
    "${RUNNING_LABEL}" \
    "${WAITING_CHECKS_LABEL}" \
    "${FAILING_CHECKS_LABEL}" \
    "${UNDER_REVIEW_LABEL}" \
    "${COVERAGE_GAP_LABEL}" \
    "${APPROVED_LABEL}" \
    "${FAILED_LABEL}" \
    "${NEEDS_HUMAN_LABEL}"; do
    if [[ "${wanted}" == *" ${label} "* ]]; then
      add_label "${issue}" "${label}"
    else
      remove_label "${issue}" "${label}"
    fi
  done
}

remove_terminal_labels() {
  local issue="$1"

  remove_label "${issue}" "${PR_LABEL}"
  remove_label "${issue}" "${RUNNING_LABEL}"
  remove_label "${issue}" "${WAITING_CHECKS_LABEL}"
  remove_label "${issue}" "${FAILING_CHECKS_LABEL}"
  remove_label "${issue}" "${UNDER_REVIEW_LABEL}"
  remove_label "${issue}" "${COVERAGE_GAP_LABEL}"
  remove_label "${issue}" "${APPROVED_LABEL}"
  remove_label "${issue}" "${FAILED_LABEL}"
  remove_label "${issue}" "${NEEDS_HUMAN_LABEL}"
  remove_label "${issue}" "${TRIGGER_LABEL}"
  remove_label "${issue}" "${QUEUE_LABEL}"
}

comment_issue() {
  local issue="$1"
  local body="$2"
  gh issue comment "${issue}" --repo "${REPO}" --body "${body}" >/dev/null
}

log_autonomous_decision() {
  local issue="$1"
  local category="$2"
  local decision="$3"
  local rationale="$4"
  local pattern="${5:-Followed existing codebase patterns}"
  local reversibility="${6:-Yes}"
  local confidence="${7:-MEDIUM}"
  local pr_number="${8:-}"

  local decisions_dir="${STATE_DIR}/autonomous-decisions"
  mkdir -p "${decisions_dir}"

  local timestamp
  timestamp=$(date +%s%3N)
  local category_slug
  category_slug=$(echo "${category}" | tr '[:upper:]' '[:lower:]' | tr '_' '-')
  local decision_id="decision-${issue}-${category_slug}-${timestamp: -5}"
  local decision_file="${decisions_dir}/${decision_id}.json"

  local needs_review="false"
  if [[ "${confidence}" == "LOW" ]] || [[ "${reversibility}" =~ [Nn]o ]] || [[ "${category}" == "SECURITY" ]]; then
    needs_review="true"
  fi

  local pr_num="null"
  if [[ -n "${pr_number}" ]]; then
    pr_num="${pr_number}"
  fi

  cat > "${decision_file}" <<EOF
{
  "id": "${decision_id}",
  "issueNumber": ${issue},
  "prNumber": ${pr_num},
  "category": "${category}",
  "decision": "${decision}",
  "rationale": "${rationale}",
  "pattern": "${pattern}",
  "reversibility": "${reversibility}",
  "confidence": "${confidence}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "needsReview": ${needs_review},
  "metadata": {
    "worker": "homedir-sdlc-worker",
    "workerVersion": "${HOMEDIR_SDLC_WORKER_VERSION:-unknown}"
  }
}
EOF

  log "Logged autonomous decision: ${decision_id} for issue #${issue}"
}

mark_needs_human() {
  local issue="$1"
  local reason="$2"
  add_label "${issue}" "${NEEDS_HUMAN_LABEL}"
  remove_label "${issue}" "${RUNNING_LABEL}"
  remove_label "${issue}" "${QUEUE_LABEL}"
  remove_label "${issue}" "${TRIGGER_LABEL}"
  remove_label "${issue}" "${WAITING_CHECKS_LABEL}"
  remove_label "${issue}" "${FAILING_CHECKS_LABEL}"
  remove_label "${issue}" "${UNDER_REVIEW_LABEL}"
  remove_label "${issue}" "${COVERAGE_GAP_LABEL}"
  remove_label "${issue}" "${APPROVED_LABEL}"
  comment_issue "${issue}" "Autonomous SDLC paused: ${reason}"
  alert WARN "Issue #${issue} needs human review" "${reason}"
}

mark_failed() {
  local issue="$1"
  local reason="$2"
  add_label "${issue}" "${FAILED_LABEL}"
  remove_label "${issue}" "${RUNNING_LABEL}"
  remove_label "${issue}" "${QUEUE_LABEL}"
  remove_label "${issue}" "${TRIGGER_LABEL}"
  remove_label "${issue}" "${WAITING_CHECKS_LABEL}"
  remove_label "${issue}" "${FAILING_CHECKS_LABEL}"
  remove_label "${issue}" "${UNDER_REVIEW_LABEL}"
  remove_label "${issue}" "${COVERAGE_GAP_LABEL}"
  remove_label "${issue}" "${APPROVED_LABEL}"
  comment_issue "${issue}" "Autonomous SDLC failed: ${reason}"
  alert FAIL "Issue #${issue} failed" "${reason}"
}

latest_trigger_labeler() {
  local issue="$1"
  gh api \
    -H "Accept: application/vnd.github+json" \
    "repos/${REPO}/issues/${issue}/timeline" --paginate \
    --jq ".[] | select(.event == \"labeled\" and .label.name == \"${TRIGGER_LABEL}\") | .actor.login" \
    2>/dev/null | tail -n1
}

# ADEV Rules #1, #4: Check issue atomicity before admission
check_issue_atomicity() {
  local body="$1"
  local number="$2"
  local criteria_count

  criteria_count=$(echo "$body" | grep -c '^\- \[ \]' || true)

  if [[ "${criteria_count}" -gt 2 ]]; then
    log "Issue #${number} has ${criteria_count} acceptance criteria (>2). Per ADEV Rule #1, issues must be atomic."

    # Check if batch delivery is explicitly requested
    if echo "$body" | grep -qi 'batch delivery\|batch mode'; then
      log "Issue #${number} has 'batch delivery' label/mention; proceeding with extended timeout"
      return 0  # Allow, but will use complex timeout (15 min)
    else
      # AUTO-SPLIT: Component #2 - Admission Auto-Processor
      log "Auto-splitting issue #${number} into ${criteria_count} atomic issues"

      if [[ -x "/home/homedir-sdlc/.local/bin/split-multi-criteria-issue.sh" ]]; then
        local split_result
        split_result=$(/home/homedir-sdlc/.local/bin/split-multi-criteria-issue.sh "${number}" 2>&1) || {
          log "ERROR: Auto-split failed for issue #${number}: ${split_result}"
          # Fallback to old behavior
          comment_issue "${number}" "This issue has ${criteria_count} acceptance criteria. Per ADEV discipline, issues should have 1-2 atomic objectives. Please either:
1. Split into separate issues (recommended), or
2. Add 'batch delivery' to the issue body and define explicit stages for each criterion."
          return 1
        }

        log "Auto-split successful for issue #${number}: ${split_result}"
        return 1  # Do not admit original (already closed by split script)
      else
        log "WARN: split-multi-criteria-issue.sh not found, falling back to manual request"
        comment_issue "${number}" "This issue has ${criteria_count} acceptance criteria. Per ADEV discipline, issues should have 1-2 atomic objectives. Please either:
1. Split into separate issues (recommended), or
2. Add 'batch delivery' to the issue body and define explicit stages for each criterion."
        return 1
      fi
    fi
  fi

  return 0
}

admit_issue_to_queue() {
  local issue="$1"
  local labeler="$2"
  add_label "${issue}" "${QUEUE_LABEL}"
  remove_label "${issue}" "${REJECTED_LABEL}"
  remove_label "${issue}" "${UNAUTHORIZED_LABEL}"
  comment_issue "${issue}" "AI SDLC admission accepted by \`${labeler}\`. The issue is now queued with \`${QUEUE_LABEL}\`."
  log "admitted issue #${issue} to ${QUEUE_LABEL}; labeler=${labeler}"
}

reject_issue_from_queue() {
  local issue="$1"
  local labeler="$2"
  remove_label "${issue}" "${TRIGGER_LABEL}"
  remove_label "${issue}" "${QUEUE_LABEL}"
  add_label "${issue}" "${REJECTED_LABEL}"
  add_label "${issue}" "${UNAUTHORIZED_LABEL}"
  comment_issue "${issue}" "AI SDLC admission rejected: \`${labeler:-unknown}\` is not authorized to add \`${TRIGGER_LABEL}\`. Authorized labelers: \`${AUTHORIZED_LABELERS}\`."
  log "rejected issue #${issue} from AI SDLC queue; labeler=${labeler:-unknown}"
}

open_pr_for_issue() {
  local issue="$1"
  {
    gh pr list \
      --repo "${REPO}" \
      --state open \
      --search "head:scc/issue-${issue}" \
      --limit 50 \
      --json number,title,url,headRefName,body \
      2>/dev/null
    gh pr list \
      --repo "${REPO}" \
      --state open \
      --search "#${issue} in:title,body" \
      --limit 100 \
      --json number,title,url,headRefName,body \
      2>/dev/null
  } \
    | jq -sc --arg issue "${issue}" '
        add
        | unique_by(.number)
        |
        [
          .[]
          | select(
              (.headRefName // "" | test("(^|/)issue-" + $issue + "([^0-9]|$)"))
              or (.title // "" | test("#" + $issue + "\\b"))
              or (.body // "" | test("(?m)^(Closes|Fixes|Resolves|Refs) #" + $issue + "\\b"))
              or (.body // "" | test("Autonomous SCC implementation for issue #" + $issue + "\\b"))
            )
        ][0] // empty
      '
}

reconcile_admission_requests() {
  local issues_json issue_json number labels labeler

  issues_json="$(gh issue list \
    --repo "${REPO}" \
    --state open \
    --label "${TRIGGER_LABEL}" \
    --limit 100 \
    --json number,labels)"

  if [[ "${issues_json}" == "[]" ]]; then
    log "reconcile_admission_requests: no admission requests found"
    return 0
  fi

  local issue_numbers
  issue_numbers="$(jq -r '.[].number' <<<"${issues_json}" | tr '\n' ',' | sed 's/,$//')"
  log "reconcile_admission_requests: processing issues: ${issue_numbers}"

  # Use process substitution instead of pipe to avoid subshell issues
  while IFS= read -r issue_json; do
    number="$(jq -r '.number' <<<"${issue_json}")"
    labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"

    log "reconcile_admission_requests: evaluating issue #${number}"

    if issue_has_label "${labels}" "${QUEUE_LABEL}" \
      || issue_has_label "${labels}" "${RUNNING_LABEL}" \
      || issue_has_label "${labels}" "${PR_LABEL}" \
      || issue_has_label "${labels}" "${WAITING_CHECKS_LABEL}" \
      || issue_has_label "${labels}" "${FAILING_CHECKS_LABEL}" \
      || issue_has_label "${labels}" "${UNDER_REVIEW_LABEL}" \
      || issue_has_label "${labels}" "${COVERAGE_GAP_LABEL}" \
      || issue_has_label "${labels}" "${APPROVED_LABEL}" \
      || issue_has_label "${labels}" "${FAILED_LABEL}" \
      || issue_has_label "${labels}" "${NEEDS_HUMAN_LABEL}" \
      || issue_has_label "${labels}" "${MERGED_LABEL}"; then
      log "reconcile_admission_requests: skipping issue #${number} (already in terminal state)"
      continue
    fi

    if ! issue_has_label "${labels}" "${ACCEPTED_LABEL}"; then
      add_label "${number}" "${ADMISSION_REVIEW_LABEL}"
      comment_issue "${number}" "AI SDLC admission is waiting for initial acceptance review. \`${TRIGGER_LABEL}\` requires \`${ACCEPTED_LABEL}\` before this issue can enter \`${QUEUE_LABEL}\`."
      log "issue #${number} has ${TRIGGER_LABEL} but is missing ${ACCEPTED_LABEL}; admission deferred"
      continue
    fi

    labeler="$(latest_trigger_labeler "${number}")"
    if is_authorized_labeler "${labeler}"; then
      # ADEV atomicity check before admission
      local issue_body
      issue_body="$(gh issue view "${number}" --repo "${REPO}" --json body --jq '.body // ""' 2>/dev/null || echo "")"
      if check_issue_atomicity "${issue_body}" "${number}"; then
        admit_issue_to_queue "${number}" "${labeler}"
      else
        log "Issue #${number} atomicity check failed; not admitting to queue"
      fi
    else
      reject_issue_from_queue "${number}" "${labeler}"
    fi
  done < <(jq -c '.[]' <<<"${issues_json}")
}

# Fix #1140: Reconcile stuck admission reviews
# Issues with ready-to-implement + scc-admission-review (without scc-accepted)
# need to be re-reviewed to converge to accepted/rejected/needs-human
reconcile_stuck_admission_reviews() {
  local issues_json issue_json number title body review_json status reason_text

  # Find issues stuck in admission review
  issues_json="$(gh issue list \
    --repo "${REPO}" \
    --state open \
    --label "${TRIGGER_LABEL}" \
    --label "${ADMISSION_REVIEW_LABEL}" \
    --limit 50 \
    --json number,title,body,labels)"

  if [[ "${issues_json}" == "[]" ]]; then
    log "reconcile_stuck_admission_reviews: no stuck issues found"
    return 0
  fi

  local issue_numbers
  issue_numbers="$(jq -r '.[].number' <<<"${issues_json}" | tr '\n' ',' | sed 's/,$//')"
  log "reconcile_stuck_admission_reviews: processing issues: ${issue_numbers}"

  # Use process substitution instead of pipe to avoid subshell issues
  while IFS= read -r issue_json; do
    number="$(jq -r '.number' <<<"${issue_json}")"
    local labels
    labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"

    log "reconcile_stuck_admission_reviews: evaluating issue #${number}"

    # Skip if already accepted, or in terminal states
    if issue_has_label "${labels}" "${ACCEPTED_LABEL}" \
      || issue_has_label "${labels}" "${REJECTED_LABEL}" \
      || issue_has_label "${labels}" "${NEEDS_HUMAN_LABEL}" \
      || issue_has_label "${labels}" "${QUEUE_LABEL}" \
      || issue_has_label "${labels}" "${RUNNING_LABEL}" \
      || issue_has_label "${labels}" "${MERGED_LABEL}"; then
      log "reconcile_stuck_admission_reviews: skipping issue #${number} (already in terminal state)"
      continue
    fi

    title="$(jq -r '.title' <<<"${issue_json}")"
    body="$(jq -r '.body // ""' <<<"${issue_json}")"

    log "Reconciling stuck admission review for issue #${number}"

    # Re-run acceptance review
    review_json="$(issue_acceptance_review "${title}" "${body}")"
    status="$(jq -r '.status' <<<"${review_json}")"
    reason_text="$(jq -r '.reasons | if length == 0 then "No blocking admission risks detected." else join(" ") end' <<<"${review_json}")"

    log "reconcile_stuck_admission_reviews: issue #${number} review result: ${status}"

    case "${status}" in
      accepted)
        add_label "${number}" "${ACCEPTED_LABEL}"
        remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
        comment_issue "${number}" "AI SDLC initial acceptance review passed (auto-reconciled). Criteria checked: improvement/correction intent, non-destructive scope, stability, security, maintainability, architecture, and good practices. ${reason_text}"
        log "Issue #${number} auto-accepted via reconciliation"
        ;;
      needs-human)
        remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
        add_label "${number}" "${NEEDS_HUMAN_LABEL}"
        comment_issue "${number}" "AI SDLC initial acceptance review needs human clarification before queue admission (auto-reconciled). ${reason_text}"
        log "Issue #${number} marked needs-human via reconciliation"
        ;;
      *)
        remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
        add_label "${number}" "${REJECTED_LABEL}"
        comment_issue "${number}" "AI SDLC initial acceptance review rejected this issue for autonomous implementation (auto-reconciled). ${reason_text}"
        log "Issue #${number} rejected via reconciliation"
        ;;
    esac
  done < <(jq -c '.[]' <<<"${issues_json}")
}

write_issue_state() {
  local issue="$1"
  local branch="$2"
  local pr_url="$3"
  local pr_number

  pr_number="$(sed -nE 's#.*/pull/([0-9]+).*#\1#p' <<<"${pr_url}" | head -n1)"
  jq -n \
    --arg issue "${issue}" \
    --arg branch "${branch}" \
    --arg pr_url "${pr_url}" \
    --arg pr_number "${pr_number}" \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{issue: ($issue|tonumber), branch: $branch, pr_url: $pr_url, pr_number: ($pr_number|tonumber?), updated_at: $updated_at}' \
    > "${ISSUE_STATE_DIR}/issue-${issue}.json"
}

update_issue_state() {
  local issue="$1"
  local jq_filter="$2"
  local state_file="${ISSUE_STATE_DIR}/issue-${issue}.json"
  local tmp_file

  if [[ ! -f "${state_file}" ]]; then
    return 0
  fi

  tmp_file="$(mktemp "${state_file}.XXXXXX")"
  jq \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "${jq_filter} | .updated_at = \$updated_at" \
    "${state_file}" > "${tmp_file}"
  mv -f "${tmp_file}" "${state_file}"
}

append_run_summary() {
  local issue="$1"
  local event="$2"
  local pr_number="$3"
  local branch="$4"
  local summary="$5"
  local file="${RUN_SUMMARY_DIR}/issue-${issue}.jsonl"

  jq -n \
    --arg repo "${REPO}" \
    --arg issue "${issue}" \
    --arg event "${event}" \
    --arg pr_number "${pr_number}" \
    --arg branch "${branch}" \
    --arg summary "${summary}" \
    --arg created_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{
      repo: $repo,
      issue: ($issue|tonumber),
      event: $event,
      pr_number: (if $pr_number == "" then null else ($pr_number|tonumber) end),
      branch: $branch,
      summary: $summary,
      created_at: $created_at
    }' >> "${file}"
}

now_epoch() {
  date -u +%s
}

iso_from_epoch() {
  local epoch="$1"
  date -u -d "@${epoch}" +%Y-%m-%dT%H:%M:%SZ
}

event_payload_file() {
  local payload_file="${1:-}"
  if [[ -z "${payload_file}" || ! -f "${payload_file}" ]]; then
    log "event command ignored: missing payload file"
    return 1
  fi
  if ! jq empty "${payload_file}" >/dev/null 2>&1; then
    log "event command ignored: invalid JSON payload ${payload_file}"
    return 1
  fi
  printf '%s\n' "${payload_file}"
}

issue_acceptance_review() {
  local title="$1"
  local body="$2"

  ISSUE_TITLE="${title}" ISSUE_BODY="${body}" python3 - <<'PY'
import json
import os
import re

title = os.environ.get("ISSUE_TITLE", "").strip()
body = os.environ.get("ISSUE_BODY", "").strip()
text = f"{title}\n{body}".lower()
reasons = []
warnings = []

if len(title) < 8 or len(body) < 30:
    warnings.append("Issue is too short to safely implement autonomously.")

destructive = [
    r"\bdelete\s+(all|prod|production|database|data|users?)\b",
    r"\bdrop\s+(database|table|schema)\b",
    r"\bwipe\s+(data|database|server|prod|production)\b",
    r"\bdisable\s+(auth|authentication|authorization|security|checks?)\b",
    r"\bbypass\s+(branch protection|rulesets?|reviews?|checks?|security)\b",
    r"\bexpose\s+(secret|token|password|key)\b",
    r"\bforce\s+push\b",
    r"\b--admin\b",
]
if any(re.search(pattern, text) for pattern in destructive):
    reasons.append("Issue appears to request destructive, unsafe, or bypass-oriented work.")

architecture = [
    r"\bremove\s+(tests?|validation|monitoring|logging)\b",
    r"\bignore\s+(security|checks?|tests?|lint|validation)\b",
]
if any(re.search(pattern, text) for pattern in architecture):
    warnings.append("Issue may degrade maintainability, safety checks, or operational guardrails.")

if reasons:
    status = "rejected"
elif warnings:
    status = "needs-human"
else:
    status = "accepted"

print(json.dumps({"status": status, "reasons": reasons + warnings}, ensure_ascii=True))
PY
}

review_new_issue_event() {
  local payload_file="$1"
  local number title body state repo review_json status reason_text

  repo="$(jq -r '.repository.full_name // ""' "${payload_file}")"
  number="$(jq -r '.issue.number // ""' "${payload_file}")"
  title="$(jq -r '.issue.title // ""' "${payload_file}")"
  body="$(jq -r '.issue.body // ""' "${payload_file}")"
  state="$(jq -r '.issue.state // ""' "${payload_file}")"

  if [[ "${repo}" != "${REPO}" || "${state}" != "open" || -z "${number}" ]]; then
    log "issue-opened ignored repo=${repo} state=${state} number=${number}"
    return 0
  fi

  add_label "${number}" "${ADMISSION_REVIEW_LABEL}"
  review_json="$(issue_acceptance_review "${title}" "${body}")"
  status="$(jq -r '.status' <<<"${review_json}")"
  reason_text="$(jq -r '.reasons | if length == 0 then "No blocking admission risks detected." else join(" ") end' <<<"${review_json}")"

  case "${status}" in
    accepted)
      add_label "${number}" "${ACCEPTED_LABEL}"
      remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
      comment_issue "${number}" "AI SDLC initial acceptance review passed. Criteria checked: improvement/correction intent, non-destructive scope, stability, security, maintainability, architecture, and good practices. ${reason_text}"
      ;;
    needs-human)
      remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
      add_label "${number}" "${NEEDS_HUMAN_LABEL}"
      comment_issue "${number}" "AI SDLC initial acceptance review needs human clarification before queue admission. ${reason_text}"
      ;;
    *)
      remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
      add_label "${number}" "${REJECTED_LABEL}"
      comment_issue "${number}" "AI SDLC initial acceptance review rejected this issue for autonomous implementation. ${reason_text}"
      ;;
  esac

  log "issue #${number} initial acceptance status=${status}"
}

write_pr_state_from_payload() {
  local payload_file="$1"
  local reason="$2"
  local force_ready="${3:-false}"
  local number repo title url head_ref base_ref state draft head_sha now not_before not_before_iso pr_state_file

  repo="$(jq -r '.repository.full_name // ""' "${payload_file}")"
  number="$(jq -r '.pull_request.number // .issue.number // ""' "${payload_file}")"
  title="$(jq -r '.pull_request.title // .issue.title // ""' "${payload_file}")"
  url="$(jq -r '.pull_request.html_url // .issue.html_url // ""' "${payload_file}")"
  head_ref="$(jq -r '.pull_request.head.ref // ""' "${payload_file}")"
  base_ref="$(jq -r '.pull_request.base.ref // ""' "${payload_file}")"
  state="$(jq -r '.pull_request.state // .issue.state // ""' "${payload_file}")"
  draft="$(jq -r '.pull_request.draft // false' "${payload_file}")"
  head_sha="$(jq -r '.pull_request.head.sha // ""' "${payload_file}")"

  if [[ "${repo}" != "${REPO}" || -z "${number}" ]]; then
    log "PR event ignored repo=${repo} number=${number}"
    return 1
  fi

  now="$(now_epoch)"
  if [[ "${force_ready}" == "true" ]]; then
    not_before="${now}"
  else
    not_before="$((now + PR_REVIEW_DELAY_SECONDS))"
  fi
  not_before_iso="$(iso_from_epoch "${not_before}")"
  pr_state_file="${PR_STATE_DIR}/pr-${number}.json"

  jq -n \
    --arg repo "${REPO}" \
    --arg number "${number}" \
    --arg title "${title}" \
    --arg url "${url}" \
    --arg head_ref "${head_ref}" \
    --arg base_ref "${base_ref}" \
    --arg state "${state}" \
    --arg draft "${draft}" \
    --arg head_sha "${head_sha}" \
    --arg reason "${reason}" \
    --argjson review_not_before_epoch "${not_before}" \
    --arg review_not_before "${not_before_iso}" \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{
      repo: $repo,
      pr_number: ($number|tonumber),
      title: $title,
      url: $url,
      head_ref: $head_ref,
      base_ref: $base_ref,
      state: $state,
      draft: ($draft == "true"),
      head_sha: $head_sha,
      last_event: $reason,
      review_not_before_epoch: $review_not_before_epoch,
      review_not_before: $review_not_before,
      updated_at: $updated_at
    }' > "${pr_state_file}"

  add_label "${number}" "${PR_TRACK_LABEL}"
  add_label "${number}" "${WAITING_CHECKS_LABEL}"
  log "tracked PR #${number} reason=${reason} review_not_before=${not_before_iso}"
}

track_pr_event() {
  local payload_file="$1"
  local reason="$2"
  local force_ready="${3:-false}"

  write_pr_state_from_payload "${payload_file}" "${reason}" "${force_ready}" || return 0
}

finalize_closed_tracked_pr() {
  local state_file="$1"
  local pr_json="$2"
  local number pr_state pr_url merged_at merge_sha summary current_summary labels_json

  number="$(jq -r '.number // .pr_number' <<<"${pr_json}")"
  pr_state="$(jq -r '.state // ""' <<<"${pr_json}")"
  pr_url="$(jq -r '.url // ""' <<<"${pr_json}")"
  merged_at="$(jq -r '.mergedAt // ""' <<<"${pr_json}")"
  merge_sha="$(jq -r '.mergeCommit.oid // ""' <<<"${pr_json}")"
  labels_json="$(jq -c '[.labels[].name]' <<<"${pr_json}")"

  if [[ "${pr_state}" == "MERGED" ]]; then
    summary="tracked PR merged"
  else
    summary="tracked PR closed without merge"
  fi

  current_summary="$(jq -r '.last_pr_state // ""' "${state_file}")"
  if [[ "${current_summary}" == "${summary}" ]] \
    && ! issue_has_label "${labels_json}" "${PR_TRACK_LABEL}" \
    && ! issue_has_label "${labels_json}" "${PR_ASSIST_LABEL}" \
    && ! issue_has_label "${labels_json}" "${WAITING_CHECKS_LABEL}" \
    && ! issue_has_label "${labels_json}" "${FAILING_CHECKS_LABEL}" \
    && ! issue_has_label "${labels_json}" "${UNDER_REVIEW_LABEL}" \
    && ! issue_has_label "${labels_json}" "${COVERAGE_GAP_LABEL}" \
    && ! issue_has_label "${labels_json}" "${APPROVED_LABEL}"; then
    return 0
  fi

  remove_label "${number}" "${PR_TRACK_LABEL}"
  remove_label "${number}" "${PR_ASSIST_LABEL}"
  remove_label "${number}" "${WAITING_CHECKS_LABEL}"
  remove_label "${number}" "${FAILING_CHECKS_LABEL}"
  remove_label "${number}" "${UNDER_REVIEW_LABEL}"
  remove_label "${number}" "${COVERAGE_GAP_LABEL}"
  remove_label "${number}" "${APPROVED_LABEL}"

  if [[ "${pr_state}" == "MERGED" ]]; then
    add_label "${number}" "${MERGED_LABEL}"
    log "cleaned tracked PR #${number} after merge"
  else
    log "cleaned tracked PR #${number} after close without merge"
  fi

  jq \
    --arg state "${pr_state}" \
    --arg pr_url "${pr_url}" \
    --arg merged_at "${merged_at}" \
    --arg merge_sha "${merge_sha}" \
    --arg summary "${summary}" \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '.state = $state
      | .url = (if $pr_url == "" then .url else $pr_url end)
      | .merged_at = (if $merged_at == "" then null else $merged_at end)
      | .merge_sha = (if $merge_sha == "" then null else $merge_sha end)
      | .last_pr_state = $summary
      | .updated_at = $updated_at' \
    "${state_file}" > "${state_file}.tmp"
  mv -f "${state_file}.tmp" "${state_file}"
}

review_tracked_pr_state() {
  local state_file="$1"
  local force="${2:-false}"
  local number now due pr_json pr_state pr_url pr_sha branch checks_json reviews_json failing_count pending_count success_count actionable_count last_reviewed_sha

  number="$(jq -r '.pr_number' "${state_file}")"
  now="$(now_epoch)"
  due="$(jq -r '.review_not_before_epoch // 0' "${state_file}")"
  if [[ "${force}" != "true" && "${now}" -lt "${due}" ]]; then
    log "PR #${number} review window still open until $(iso_from_epoch "${due}")"
    return 0
  fi

  pr_json="$(gh pr view "${number}" \
    --repo "${REPO}" \
    --json number,title,body,state,isDraft,url,headRefName,headRefOid,reviewDecision,latestReviews,statusCheckRollup,labels,mergedAt,mergeCommit \
    2>/dev/null || true)"
  if [[ -z "${pr_json}" ]]; then
    return 0
  fi

  pr_state="$(jq -r '.state // ""' <<<"${pr_json}")"
  if [[ "${pr_state}" != "OPEN" ]]; then
    finalize_closed_tracked_pr "${state_file}" "${pr_json}"
    return 0
  fi

  pr_url="$(jq -r '.url // ""' <<<"${pr_json}")"
  pr_sha="$(jq -r '.headRefOid // ""' <<<"${pr_json}")"
  branch="$(jq -r '.headRefName // ""' <<<"${pr_json}")"
  checks_json="$(pr_checks_state "${pr_json}")"
  reviews_json="$(review_state "${pr_json}")"
  failing_count="$(jq -r '.failing | length' <<<"${checks_json}")"
  pending_count="$(jq -r '.pending | length' <<<"${checks_json}")"
  success_count="$(jq -r '.successful | length' <<<"${checks_json}")"
  actionable_count="$(jq -r '.actionable_reviews | length' <<<"${reviews_json}")"
  last_reviewed_sha="$(jq -r '.last_reviewed_sha // ""' "${state_file}")"

  if [[ "${branch}" =~ ^scc/issue-([0-9]+) ]]; then
    local issue="${BASH_REMATCH[1]}"
    if [[ ! -f "${ISSUE_STATE_DIR}/issue-${issue}.json" ]]; then
      write_issue_state "${issue}" "${branch}" "${pr_url}"
    fi
    reconcile_pr_state "${ISSUE_STATE_DIR}/issue-${issue}.json"
    return 0
  fi

  if [[ "${last_reviewed_sha}" == "${pr_sha}" && "${force}" != "true" ]]; then
    return 0
  fi

  if [[ "${failing_count}" -gt 0 ]]; then
    set_flow_labels "${number}" "${PR_TRACK_LABEL}" "${FAILING_CHECKS_LABEL}" "${UNDER_REVIEW_LABEL}"
    comment_issue "${number}" "AI SDLC review: PR checks are failing after the review window. Please inspect failing checks before merge. Context: $(jq -c '.failing' <<<"${checks_json}")"
  elif [[ "${pending_count}" -gt 0 || "${success_count}" -eq 0 ]]; then
    set_flow_labels "${number}" "${PR_TRACK_LABEL}" "${WAITING_CHECKS_LABEL}"
    log "tracked PR #${number} still waiting for checks"
  elif [[ "${actionable_count}" -gt 0 ]]; then
    set_flow_labels "${number}" "${PR_TRACK_LABEL}" "${UNDER_REVIEW_LABEL}"
    comment_issue "${number}" "AI SDLC review: actionable review feedback is present. Context: $(jq -c '.actionable_reviews' <<<"${reviews_json}")"
  else
    set_flow_labels "${number}" "${PR_TRACK_LABEL}" "${APPROVED_LABEL}"
    comment_issue "${number}" "AI SDLC review passed for collaborator PR ${pr_url}: checks are green and no actionable review feedback was detected. Repository rules still apply."
  fi

  PR_SHA="${pr_sha}" jq \
    --arg updated_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '.last_reviewed_sha = env.PR_SHA | .last_reviewed_at = $updated_at' \
    "${state_file}" > "${state_file}.tmp"
  mv -f "${state_file}.tmp" "${state_file}"
}

reconcile_tracked_prs() {
  local state_file

  while IFS= read -r state_file; do
    review_tracked_pr_state "${state_file}"
  done < <(find "${PR_STATE_DIR}" -maxdepth 1 -name 'pr-*.json' -type f 2>/dev/null)
}

pr_checks_state() {
  local pr_json="$1"
  jq -r '
    def check_name: (.name // .context // "unknown");
    def check_status: (.status // .state // "");
    def check_conclusion: (.conclusion // .state // "");
    def is_failure:
      (check_conclusion | ascii_downcase) as $c
      | ($c == "failure" or $c == "error" or $c == "timed_out" or $c == "cancelled");
    def is_pending:
      (check_status | ascii_downcase) as $s
      | (check_conclusion | ascii_downcase) as $c
      | ($s == "queued" or $s == "in_progress" or $s == "pending" or $c == "");
    {
      failing: [
        (.statusCheckRollup // [])[]
        | select(is_failure)
        | {name: check_name, conclusion: check_conclusion, url: (.detailsUrl // .targetUrl // "")}
      ],
      pending: [
        (.statusCheckRollup // [])[]
        | select(is_pending)
        | {name: check_name, status: check_status, url: (.detailsUrl // .targetUrl // "")}
      ],
      successful: [
        (.statusCheckRollup // [])[]
        | select((check_conclusion | ascii_downcase) == "success")
        | {name: check_name, url: (.detailsUrl // .targetUrl // "")}
      ]
    }
  ' <<<"${pr_json}"
}

review_state() {
  local pr_json="$1"
  jq -r '
    def actionable_count:
      (try ((.body // "") | capture("Actionable comments posted: (?<n>[0-9]+)").n | tonumber) catch 0);
    {
      review_decision: (.reviewDecision // ""),
      actionable_reviews: [
        (.latestReviews // [])[]
        | select((.state // "") == "CHANGES_REQUESTED" or actionable_count > 0)
        | {
            author: (.author.login // "unknown"),
            state: (.state // ""),
            actionable: actionable_count,
            body: ((.body // "") | gsub("\r"; "") | split("\n") | map(select(length > 0)) | .[0:120] | join("\n"))
          }
      ]
    }
  ' <<<"${pr_json}"
}

issue_coverage_state() {
  local issue="$1"
  local pr_json="$2"
  local issue_body pr_body

  issue_body="$(gh issue view "${issue}" --repo "${REPO}" --json body --jq '.body // ""' 2>/dev/null || true)"
  pr_body="$(jq -r '.body // ""' <<<"${pr_json}")"

  ISSUE_BODY="${issue_body}" PR_BODY="${pr_body}" python3 - <<'PY'
import json
import os
import re

issue = os.environ.get("ISSUE_BODY", "")
pr = os.environ.get("PR_BODY", "")
gaps = []
specific_items = []
criteria_heading_pattern = r"(?im)^\s*(?:#{1,6}\s*)?(acceptance criteria|criterios de aceptaci[o\u00f3]n|definition of done|definici[o\u00f3]n de list[oa]s?)\b"
criteria_item_pattern = r"(?im)acceptance criteria|acceptance criterion|criterios de aceptaci[o\u00f3]n|criterio de aceptaci[o\u00f3]n|definition of done|definici[o\u00f3]n de list[oa]s?"

coverage_match = re.search(r"(?ims)^##\s+Issue Coverage\s*$([\s\S]*?)(?=^##\s+|\Z)", pr)
coverage = coverage_match.group(1).strip() if coverage_match else ""

if not coverage_match:
    gaps.append("PR body is missing a `## Issue Coverage` section.")
elif re.search(r"(?m)^\s*[-*]\s+\[\s\]", coverage):
    gaps.append("`## Issue Coverage` still contains unchecked items.")
else:
    # Extract checked items with their sub-bullets for context
    coverage_blocks = re.findall(r"(?m)^\s*[-*]\s+\[[xX]\]\s+(.+?)(?=^\s*[-*]\s+\[|^##|\Z)", coverage, re.DOTALL)

    generic_patterns = [
        r"^addresses issue #[0-9]+:",
        r"^map concrete code changes to issue #[0-9]+:",
        r"^map each acceptance criterion",
        r"^list any known uncovered requirement",
        r"^implements the requested issue scope",
        r"^maps acceptance criteria and technical observations",
        r"^leaves no known issue requirement",
        r"^source issue requirements reviewed",
    ]

    # Evidence indicators that make an item specific even if header is generic
    evidence_patterns = [
        r"Evidence:",
        r"SATISFIED",
        r"Criterion \d+:",
        r"line \d+",
        r"`[^`]+\.(ts|js|java|py|sh|yml|yaml|json|md|txt|example)`",  # File paths
        r"\u2192\s*\*\*",  # \u2192 **SATISFIED** pattern
        r"(?:known gaps?|uncovered)\s*:\s*none known\.",  # Explicit statement of no gaps, scoped to label
    ]

    for block in coverage_blocks:
        first_line = block.split('\n')[0].strip()
        full_block = block.strip()

        # Check if item is generic without evidence
        is_generic_header = any(re.search(pattern, first_line, re.I) for pattern in generic_patterns)
        has_evidence = any(re.search(pattern, full_block, re.I) for pattern in evidence_patterns)

        # Item is specific if it's not generic OR if it has evidence markers
        if not is_generic_header or has_evidence:
            specific_items.append(full_block)

    if not specific_items:
        gaps.append("`## Issue Coverage` only contains generic boilerplate; add issue-specific coverage evidence.")

has_acceptance = bool(re.search(criteria_heading_pattern, issue))
acceptance_items = [
    item for item in specific_items
    if re.search(criteria_item_pattern, item)
]
if has_acceptance and not acceptance_items:
    gaps.append("Issue has explicit acceptance criteria, but PR body does not map them.")

validation_match = re.search(r"(?ims)^##\s+Validation\s*$([\s\S]*?)(?=^##\s+|\Z)", pr)
validation = validation_match.group(1).strip() if validation_match else ""
if not validation_match:
    gaps.append("PR body is missing validation evidence.")
elif not validation or re.search(r"not run by worker", validation, re.I):
    gaps.append("`## Validation` contains placeholder or missing validation evidence.")

print(json.dumps({"passed": not gaps, "gaps": gaps}, ensure_ascii=True))
PY
}

build_remediation_prompt() {
  local issue="$1"
  local title="$2"
  local branch="$3"
  local pr_url="$4"
  local checks_json="$5"
  local reviews_json="$6"
  local trigger="$7"

  cat <<EOF
Continue the autonomous SDLC remediation for ${REPO} issue #${issue}.

Issue title:
${title}

PR:
${pr_url}

Branch:
${branch}

Reason for this remediation cycle:
${trigger}

Failing or pending check context:
$(jq -r '.' <<<"${checks_json}")

Review or coverage context:
$(jq -r '.' <<<"${reviews_json}")

Rules:
- Stay on branch ${branch}; never push directly to main.
- Fix only the failing checks or actionable review feedback shown above.
- If the trigger is a coverage gap, update the implementation and PR body so the section named ## Issue Coverage truthfully maps code changes to the issue request and acceptance criteria.
- If the implementation is incomplete, make the missing code, test, workflow, or documentation changes instead of only describing them.
- If only the PR body is incomplete, update it directly with gh pr edit so the coverage section contains checked, evidence-backed items.
- Do not stop at analysis, recommendations, or requests for approval when the requested remediation is actionable.
- Keep the change minimal and within the issue/PR scope.
- Run the smallest meaningful validation you can.
- Do not use --admin.
- Do not bypass branch protection, required checks, reviews, repository rulesets, or secrets.
- Leave files changed but do not force-push unless necessary; the worker will commit and push.
- If the feedback is unsafe, ambiguous, or cannot be fixed with code, stop after leaving a clear note in the working tree or PR context.
EOF
}

run_scc_on_existing_pr() {
  local issue="$1"
  local title="$2"
  local branch="$3"
  local pr_number="$4"
  local pr_url="$5"
  local checks_json="$6"
  local reviews_json="$7"
  local trigger="$8"
  local prompt validation_summary attempts

  attempts="$(jq -r '.remediation_attempts // 0' "${ISSUE_STATE_DIR}/issue-${issue}.json" 2>/dev/null || echo 0)"
  if [[ "${attempts}" -ge "${MAX_REMEDIATION_ATTEMPTS}" ]]; then
    mark_needs_human "${issue}" "Autonomous remediation reached ${MAX_REMEDIATION_ATTEMPTS} attempts for PR #${pr_number}."
    return 0
  fi

  log "running SCC remediation for issue #${issue} PR #${pr_number}: ${trigger}"
  write_heartbeat "running" "SCC remediation for issue #${issue}"
  if [[ "${trigger}" == *"coverage gap"* ]]; then
    set_flow_labels "${issue}" "${PR_LABEL}" "${RUNNING_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}"
  else
    set_flow_labels "${issue}" "${PR_LABEL}" "${RUNNING_LABEL}" "${UNDER_REVIEW_LABEL}"
  fi

  prepare_workdir
  git -C "${WORKDIR}" checkout -B "${branch}" "origin/${branch}"

  prompt="$(build_remediation_prompt "${issue}" "${title}" "${branch}" "${pr_url}" "${checks_json}" "${reviews_json}" "${trigger}")"
  if run_scc_with_timeout_handling "${prompt}"; then
    :
  else
    local scc_rc=$?
    if [[ "${scc_rc}" -eq 124 ]]; then
      mark_failed "${issue}" "SCC remediation timed out after ${SCC_TIMEOUT_SECONDS}s for PR #${pr_number}. Check ${LOGFILE} on the runner."
      return 0
    fi
    mark_failed "${issue}" "SCC remediation exited non-zero (${scc_rc}) for PR #${pr_number}. Check ${LOGFILE} on the runner."
    return 0
  fi

  if [[ "$(git -C "${WORKDIR}" branch --show-current)" == "main" ]]; then
    mark_needs_human "${issue}" "SCC remediation ended on main. Refusing to continue."
    return 0
  fi

  if [[ -z "$(git -C "${WORKDIR}" status --porcelain)" ]]; then
    update_issue_state "${issue}" '.remediation_attempts = ((.remediation_attempts // 0) + 1) | .last_remediation_noop_at = $updated_at'
    attempts="$((attempts + 1))"
    if [[ "${attempts}" -ge "${MAX_REMEDIATION_ATTEMPTS}" ]]; then
      mark_needs_human "${issue}" "SCC remediation for PR #${pr_number} produced no changes after ${attempts} attempts."
    else
      if [[ "${trigger}" == *"coverage gap"* ]]; then
        set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}"
      else
        set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}"
      fi
      append_run_summary "${issue}" "remediation-noop" "${pr_number}" "${branch}" "SCC remediation produced no changes for ${trigger}; attempt ${attempts}/${MAX_REMEDIATION_ATTEMPTS}."
      comment_issue "${issue}" "Autonomous SDLC remediation produced no changes for PR #${pr_number}. Attempt ${attempts}/${MAX_REMEDIATION_ATTEMPTS}; state remains \`${UNDER_REVIEW_LABEL}\`."
    fi
    return 0
  fi

  git -C "${WORKDIR}" add -A
  git -C "${WORKDIR}" commit -m "fix(sdlc): remediate issue #${issue} PR checks" -m "PR #${pr_number}"

  validation_summary="Worker validation command not configured; GitHub checks are required before approval."
  if [[ -n "${VALIDATION_COMMAND}" ]]; then
    log "running validation for issue #${issue} remediation: ${VALIDATION_COMMAND}"
    if (cd "${WORKDIR}" && bash -lc "${VALIDATION_COMMAND}"); then
      validation_summary="\`${VALIDATION_COMMAND}\` passed"
    else
      mark_failed "${issue}" "Validation command failed during remediation: \`${VALIDATION_COMMAND}\`."
      return 0
    fi
  fi

  if ! git -C "${WORKDIR}" push origin "${branch}" 2>/dev/null; then
    git -C "${WORKDIR}" fetch origin "${branch}" >/dev/null 2>&1 || true
    if git -C "${WORKDIR}" merge-base --is-ancestor HEAD "origin/${branch}" >/dev/null 2>&1; then
      log "push for issue #${issue} reported failure, but origin/${branch} already contains local remediation"
    else
      mark_failed "${issue}" "Git push failed during remediation for branch ${branch}."
      return 0
    fi
  fi

  update_issue_state "${issue}" '.remediation_attempts = ((.remediation_attempts // 0) + 1) | .last_remediation_at = $updated_at'
  set_flow_labels "${issue}" "${PR_LABEL}" "${WAITING_CHECKS_LABEL}"
  append_run_summary "${issue}" "remediation-pushed" "${pr_number}" "${branch}" "Remediation pushed for ${trigger}. Validation: ${validation_summary}"
  comment_issue "${issue}" "Autonomous SDLC remediation pushed to PR #${pr_number}. State: \`${WAITING_CHECKS_LABEL}\`. Validation: ${validation_summary}"
}

release_status_for_pr() {
  local pr_number="$1"
  local pr_json merge_sha merge_date runs_json run_status run_conclusion run_url

  pr_json="$(gh pr view "${pr_number}" --repo "${REPO}" --json mergeCommit,mergedAt,url)"
  merge_sha="$(jq -r '.mergeCommit.oid // ""' <<<"${pr_json}")"
  if [[ -z "${merge_sha}" ]]; then
    echo "pending|No merge commit is available yet|"
    return 0
  fi

  runs_json="$(gh run list \
    --repo "${REPO}" \
    --workflow release.yml \
    --commit "${merge_sha}" \
    --limit 1 \
    --json status,conclusion,url \
    --jq '.')"

  if [[ "${runs_json}" == "[]" ]]; then
    echo "pending|Production Release has not started for ${merge_sha}|"
    return 0
  fi

  run_status="$(jq -r '.[0].status // ""' <<<"${runs_json}")"
  run_conclusion="$(jq -r '.[0].conclusion // ""' <<<"${runs_json}")"
  run_url="$(jq -r '.[0].url // ""' <<<"${runs_json}")"

  if [[ "${run_status}" == "completed" && "${run_conclusion}" == "success" ]]; then
    merge_date="$(jq -r '.mergedAt // ""' <<<"${pr_json}")"
    echo "success|Production Release succeeded|${run_url}|${merge_sha}|${merge_date}"
  elif [[ "${run_status}" == "completed" ]]; then
    echo "failure|Production Release completed with conclusion: ${run_conclusion}|${run_url}|${merge_sha}|"
  else
    echo "pending|Production Release is ${run_status}|${run_url}|${merge_sha}|"
  fi
}

try_enable_auto_merge() {
  local issue="$1"
  local branch="$2"
  local pr_number="${3:-}"
  local pr_url="${4:-}"

  if [[ -z "${pr_number}" && -n "${pr_url}" ]]; then
    pr_number="$(sed -nE 's#.*/pull/([0-9]+).*#\1#p' <<<"${pr_url}" | head -n1)"
  fi

  try_enable_auto_merge_for_pr "issue #${issue}" "${branch}" "${pr_number}" "${pr_url}"
}

try_enable_auto_merge_for_pr() {
  local context="$1"
  local branch="$2"
  local pr_number="${3:-}"
  local pr_url="${4:-}"
  local merge_target="${branch}"

  if [[ -z "${merge_target}" || "${merge_target}" == "null" ]]; then
    merge_target="${pr_number}"
  fi

  if [[ "${ENABLE_AUTOMERGE}" != "true" ]]; then
    return 1
  fi

  if gh pr merge "${merge_target}" --repo "${REPO}" --squash --auto >/dev/null 2>&1; then
    log "enabled normal auto-merge for ${context} (branch=${branch} pr=#${pr_number})"
    return 0
  fi

  log "auto-merge not available for ${context} (branch=${branch} pr=#${pr_number} url=${pr_url})"
  return 1
}

enable_auto_merge_for_state() {
  local state_file="$1"
  local issue pr_number branch pr_json pr_state auto_merge pr_url

  issue="$(jq -r '.issue' "${state_file}")"
  pr_number="$(jq -r '.pr_number // ""' "${state_file}")"
  branch="$(jq -r '.branch' "${state_file}")"

  if [[ -z "${pr_number}" || "${pr_number}" == "null" ]]; then
    return 0
  fi

  pr_json="$(gh pr view "${pr_number}" --repo "${REPO}" --json state,autoMergeRequest,url 2>/dev/null || true)"
  if [[ -z "${pr_json}" ]]; then
    return 0
  fi

  pr_state="$(jq -r '.state' <<<"${pr_json}")"
  auto_merge="$(jq -r '.autoMergeRequest != null' <<<"${pr_json}")"
  pr_url="$(jq -r '.url' <<<"${pr_json}")"

  if [[ "${pr_state}" != "OPEN" || "${auto_merge}" == "true" ]]; then
    return 0
  fi

  if ! try_enable_auto_merge "${issue}" "${branch}" "${pr_number}" "${pr_url}"; then
    add_label "${issue}" "${NEEDS_HUMAN_LABEL}"
    comment_issue "${issue}" "Autonomous SDLC could not enable normal auto-merge for ${pr_url}. Repository rules still apply; no admin bypass was used."
    alert WARN "Issue #${issue} auto-merge blocked" "Could not enable normal auto-merge for ${pr_url}. Repository rules still apply; no admin bypass was used."
  fi
}

issue_title_for_prompt() {
  local issue="$1"
  local fallback_title="$2"
  local title

  title="$(gh issue view "${issue}" --repo "${REPO}" --json title --jq '.title' 2>/dev/null || true)"
  if [[ -n "${title}" ]]; then
    printf '%s\n' "${title}"
  else
    printf '%s\n' "${fallback_title}"
  fi
}

reconcile_pr_state() {
  local state_file="$1"
  local issue pr_number branch pr_json pr_state pr_url pr_title issue_title pr_sha is_draft checks_json reviews_json
  local coverage_json coverage_passed failing_count pending_count success_count actionable_count trigger approved_sha

  issue="$(jq -r '.issue' "${state_file}")"
  pr_number="$(jq -r '.pr_number // ""' "${state_file}")"
  branch="$(jq -r '.branch' "${state_file}")"

  if [[ -z "${pr_number}" || "${pr_number}" == "null" ]]; then
    return 0
  fi

  pr_json="$(gh pr view "${pr_number}" \
    --repo "${REPO}" \
    --json number,title,body,state,isDraft,url,headRefName,headRefOid,mergeStateStatus,mergeable,reviewDecision,latestReviews,statusCheckRollup,autoMergeRequest \
    2>/dev/null || true)"
  if [[ -z "${pr_json}" ]]; then
    return 0
  fi

  pr_state="$(jq -r '.state // ""' <<<"${pr_json}")"
  pr_url="$(jq -r '.url // ""' <<<"${pr_json}")"
  pr_title="$(jq -r '.title // ""' <<<"${pr_json}")"
  issue_title="$(issue_title_for_prompt "${issue}" "${pr_title}")"
  pr_sha="$(jq -r '.headRefOid // ""' <<<"${pr_json}")"
  is_draft="$(jq -r '.isDraft // false' <<<"${pr_json}")"
  checks_json="$(pr_checks_state "${pr_json}")"
  reviews_json="$(review_state "${pr_json}")"
  coverage_json="$(issue_coverage_state "${issue}" "${pr_json}")"
  failing_count="$(jq -r '.failing | length' <<<"${checks_json}")"
  pending_count="$(jq -r '.pending | length' <<<"${checks_json}")"
  success_count="$(jq -r '.successful | length' <<<"${checks_json}")"
  actionable_count="$(jq -r '.actionable_reviews | length' <<<"${reviews_json}")"
  coverage_passed="$(jq -r '.passed' <<<"${coverage_json}")"

  if [[ "${pr_state}" != "OPEN" ]]; then
    return 0
  fi

  if [[ "${failing_count}" -gt 0 ]]; then
    trigger="failing checks on PR #${pr_number}"
    set_flow_labels "${issue}" "${PR_LABEL}" "${FAILING_CHECKS_LABEL}" "${UNDER_REVIEW_LABEL}"
    update_issue_state "${issue}" '.last_pr_state = "failing-checks" | .last_checked_at = $updated_at'
    run_scc_on_existing_pr "${issue}" "${issue_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${reviews_json}" "${trigger}"
    return 0
  fi

  if [[ "${pending_count}" -gt 0 || "${success_count}" -eq 0 || "${is_draft}" == "true" ]]; then
    set_flow_labels "${issue}" "${PR_LABEL}" "${WAITING_CHECKS_LABEL}"
    update_issue_state "${issue}" '.last_pr_state = "waiting-checks" | .last_checked_at = $updated_at'
    log "issue #${issue} PR #${pr_number} waiting for checks/review"
    return 0
  fi

  if [[ "${actionable_count}" -gt 0 ]]; then
    trigger="actionable review feedback on PR #${pr_number}"
    set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}"
    update_issue_state "${issue}" '.last_pr_state = "under-review" | .last_checked_at = $updated_at'
    run_scc_on_existing_pr "${issue}" "${issue_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${reviews_json}" "${trigger}"
    return 0
  fi

  if [[ "${coverage_passed}" != "true" ]]; then
    trigger="technical issue coverage gap on PR #${pr_number}: $(jq -r '.gaps | join("; ")' <<<"${coverage_json}")"
    set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}"
    update_issue_state "${issue}" '.last_pr_state = "coverage-gap" | .last_checked_at = $updated_at'
    run_scc_on_existing_pr "${issue}" "${issue_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${coverage_json}" "${trigger}"
    return 0
  fi

  set_flow_labels "${issue}" "${PR_LABEL}" "${APPROVED_LABEL}"
  approved_sha="$(jq -r '.approved_sha // ""' "${state_file}")"
  if [[ "${approved_sha}" != "${pr_sha}" ]]; then
    append_run_summary "${issue}" "approved" "${pr_number}" "${branch}" "All checks passed and no actionable review feedback remains for ${pr_url}."
    APPROVED_SHA="${pr_sha}" update_issue_state "${issue}" '.last_pr_state = "approved" | .approved_sha = env.APPROVED_SHA | .approved_at = $updated_at'
    comment_issue "${issue}" "Autonomous SDLC approved PR #${pr_number}: all checks passed and no actionable review feedback remains. Normal auto-merge can now be enabled under repository rules."
  else
    update_issue_state "${issue}" '.last_pr_state = "approved" | .last_checked_at = $updated_at'
  fi

  enable_auto_merge_for_state "${state_file}"
}

reconcile_open_prs() {
  local state_file

  while IFS= read -r state_file; do
    reconcile_pr_state "${state_file}"
  done < <(find "${ISSUE_STATE_DIR}" -maxdepth 1 -name 'issue-*.json' -type f 2>/dev/null)
}

# Fix #1142: Reconcile orphan PRs (PRs without state files)
reconcile_orphan_open_prs() {
  local prs_json pr_json pr_number pr_url branch labels auto_merge is_draft checks_json
  local failing_count pending_count success_count

  prs_json="$(gh pr list \
    --repo "${REPO}" \
    --state open \
    --search "label:${PR_TRACK_LABEL} label:${APPROVED_LABEL}" \
    --limit 100 \
    --json number,title,url,headRefName,isDraft,mergeStateStatus,mergeable,statusCheckRollup,autoMergeRequest,labels \
    2>/dev/null || echo '[]')"

  if [[ -z "${prs_json}" || "${prs_json}" == "null" ]]; then
    prs_json="[]"
  fi

  if [[ "${prs_json}" == "[]" ]]; then
    log "reconcile_orphan_open_prs: no orphan PRs found"
    return 0
  fi

  local pr_numbers
  pr_numbers="$(jq -r '.[].number' <<<"${prs_json}" | tr '\n' ',' | sed 's/,$//')"
  log "reconcile_orphan_open_prs: processing PRs: ${pr_numbers}"

  # Use process substitution instead of pipe to avoid subshell issues
  while IFS= read -r pr_json; do
    pr_number="$(jq -r '.number // ""' <<<"${pr_json}")"
    pr_url="$(jq -r '.url // ""' <<<"${pr_json}")"
    branch="$(jq -r '.headRefName // ""' <<<"${pr_json}")"
    labels="$(jq -c '[.labels[].name]' <<<"${pr_json}")"
    auto_merge="$(jq -r '.autoMergeRequest != null' <<<"${pr_json}")"
    is_draft="$(jq -r '.isDraft // false' <<<"${pr_json}")"

    if [[ -z "${pr_number}" || "${pr_number}" == "null" ]]; then
      continue
    fi

    if ! issue_has_label "${labels}" "${PR_TRACK_LABEL}" || ! issue_has_label "${labels}" "${APPROVED_LABEL}"; then
      continue
    fi

    if [[ "${auto_merge}" == "true" ]]; then
      log "approved tracked PR #${pr_number} already has auto-merge enabled"
      continue
    fi

    checks_json="$(pr_checks_state "${pr_json}")"
    failing_count="$(jq -r '.failing | length' <<<"${checks_json}")"
    pending_count="$(jq -r '.pending | length' <<<"${checks_json}")"
    success_count="$(jq -r '.successful | length' <<<"${checks_json}")"

    if [[ "${is_draft}" == "true" || "${failing_count}" -gt 0 || "${pending_count}" -gt 0 || "${success_count}" -eq 0 ]]; then
      log "approved tracked PR #${pr_number} is not clean for orphan auto-merge reconciliation (draft=${is_draft} failing=${failing_count} pending=${pending_count} successful=${success_count})"
      continue
    fi

    log "reconciling approved tracked PR #${pr_number} without issue state file"
    if ! try_enable_auto_merge_for_pr "orphan approved tracked PR #${pr_number}" "${branch}" "${pr_number}" "${pr_url}"; then
      add_label "${pr_number}" "${NEEDS_HUMAN_LABEL}"
      comment_issue "${pr_number}" "Autonomous SDLC could not enable normal auto-merge for approved tracked PR ${pr_url}. Repository rules still apply; no admin bypass was used."
      alert WARN "PR #${pr_number} auto-merge blocked" "Could not enable normal auto-merge for approved tracked PR ${pr_url}. Repository rules still apply; no admin bypass was used."
    fi
  done < <(jq -c '.[]' <<<"${prs_json}")
}

finalize_merged_issue() {
  local number="$1"
  local pr_number="$2"
  local pr_url="$3"
  local release_status release_message release_url merge_sha merged_at labels

  IFS='|' read -r release_status release_message release_url merge_sha merged_at < <(release_status_for_pr "${pr_number}")

  if [[ "${release_status}" == "pending" ]]; then
    log "issue #${number} PR #${pr_number} merged; waiting for release verification: ${release_message}"
    return 0
  fi

  if [[ "${release_status}" == "failure" ]]; then
    labels="$(gh issue view "${number}" --repo "${REPO}" --json labels --jq '[.labels[].name]' 2>/dev/null || echo '[]')"
    if issue_has_label "${labels}" "${NEEDS_HUMAN_LABEL}"; then
      log "issue #${number} release verification still failing for PR #${pr_number}; ${NEEDS_HUMAN_LABEL} already present"
      return 0
    fi
    add_label "${number}" "${NEEDS_HUMAN_LABEL}"
    comment_issue "${number}" "Autonomous SDLC merge completed, but release verification failed for PR #${pr_number}: ${release_message} ${release_url}"
    log "issue #${number} release verification failed for PR #${pr_number}"
    alert FAIL "Issue #${number} release failed" "PR #${pr_number}: ${release_message} ${release_url}"
    return 0
  fi

  add_label "${number}" "${MERGED_LABEL}"
  remove_terminal_labels "${number}"
  append_run_summary "${number}" "completed" "${pr_number}" "" "PR #${pr_number} merged at ${merged_at} (${merge_sha}) and production release verification succeeded. ${release_url}"
  comment_issue "${number}" "Autonomous SDLC completed: PR #${pr_number} was merged (${pr_url}) at ${merged_at}. Merge commit: \`${merge_sha}\`. Production release succeeded: ${release_url}"
  gh issue close "${number}" --repo "${REPO}" --comment "Closed by autonomous SDLC after PR #${pr_number} was merged and production release verification succeeded. Release: ${release_url}" >/dev/null 2>&1 || true
  log "closed issue #${number} via PR #${pr_number}; release verified"
  alert INFO "Issue #${number} deployed" "PR #${pr_number} was merged and Production Release succeeded. ${release_url}"

  # Pipeline orchestration: trigger next issue in pipeline
  local orchestrator_script="${HOME}/platform/scripts/pipeline-orchestrator.sh"
  if [[ -x "${orchestrator_script}" ]]; then
    log "calling pipeline orchestrator for completed issue #${number}"
    "${orchestrator_script}" "${number}" 2>&1 | while IFS= read -r line; do
      log "[orchestrator] ${line}"
    done || log "WARNING: pipeline orchestrator failed for issue #${number}"
  fi
}

reconcile_merged_prs() {
  local state_file issue pr_number pr_url labels pr_json pr_state resolved_pr_url

  while IFS= read -r state_file; do
    issue="$(jq -r '.issue // ""' "${state_file}")"
    pr_number="$(jq -r '.pr_number // ""' "${state_file}")"
    pr_url="$(jq -r '.pr_url // ""' "${state_file}")"

    if [[ -z "${issue}" || -z "${pr_number}" || "${pr_number}" == "null" ]]; then
      continue
    fi

    labels="$(gh issue view "${issue}" --repo "${REPO}" --json labels --jq '[.labels[].name]' 2>/dev/null || echo '[]')"
    if issue_has_label "${labels}" "${MERGED_LABEL}"; then
      continue
    fi

    pr_json="$(gh pr view "${pr_number}" --repo "${REPO}" --json state,url 2>/dev/null || true)"
    if [[ -z "${pr_json}" ]]; then
      continue
    fi

    pr_state="$(jq -r '.state // ""' <<<"${pr_json}")"
    resolved_pr_url="$(jq -r '.url // ""' <<<"${pr_json}")"
    if [[ -n "${resolved_pr_url}" ]]; then
      pr_url="${resolved_pr_url}"
    fi
    if [[ "${pr_state}" != "MERGED" ]]; then
      continue
    fi

    finalize_merged_issue "${issue}" "${pr_number}" "${pr_url}"
  done < <(find "${ISSUE_STATE_DIR}" -maxdepth 1 -name 'issue-*.json' -type f 2>/dev/null)
}

reconcile_legacy_closed_issue() {
  local issue_json="$1"
  local number labels issue_detail prs_json pr_number pr_url

  number="$(jq -r '.number' <<<"${issue_json}")"
  labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"

  if issue_has_label "${labels}" "${MERGED_LABEL}"; then
    return 0
  fi

  issue_detail="$(gh issue view "${number}" \
    --repo "${REPO}" \
    --json number,state,closedByPullRequestsReferences,labels)"

  prs_json="$(jq -c '.closedByPullRequestsReferences // []' <<<"${issue_detail}")"
  if [[ "$(jq 'length' <<<"${prs_json}")" -eq 0 ]]; then
    log "closed issue #${number} has autonomous labels but no closing PR reference; cleaning terminal labels"
    remove_terminal_labels "${number}"
    return 0
  fi

  pr_number="$(jq -r '.[0].number' <<<"${prs_json}")"
  pr_url="$(jq -r '.[0].url' <<<"${prs_json}")"
  finalize_merged_issue "${number}" "${pr_number}" "${pr_url}"
}

reconcile_legacy_closed_issues() {
  local issues_json issue_json label

  issues_json="$(
    for label in "${PR_LABEL}" "${RUNNING_LABEL}" "${WAITING_CHECKS_LABEL}" "${FAILING_CHECKS_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}" "${APPROVED_LABEL}" "${NEEDS_HUMAN_LABEL}" "${FAILED_LABEL}" "${TRIGGER_LABEL}"; do
      gh issue list \
        --repo "${REPO}" \
        --state closed \
        --label "${label}" \
        --limit 100 \
        --json number,labels
    done | jq -s 'add | unique_by(.number)'
  )"

  if [[ "${issues_json}" != "[]" ]]; then
    local issue_numbers
    issue_numbers="$(jq -r '.[].number' <<<"${issues_json}" | tr '\n' ',' | sed 's/,$//')"
    log "reconcile_legacy_closed_issues: processing issues: ${issue_numbers}"

    # Use process substitution instead of pipe to avoid subshell issues
    while IFS= read -r issue_json; do
      reconcile_legacy_closed_issue "${issue_json}"
    done < <(jq -c '.[]' <<<"${issues_json}")
  else
    log "reconcile_legacy_closed_issues: no legacy closed issues found"
  fi
}

prepare_workdir() {
  if [[ ! -d "${WORKDIR}/.git" ]]; then
    mkdir -p "$(dirname "${WORKDIR}")"
    git clone "https://github.com/${REPO}.git" "${WORKDIR}"
  fi

  git -C "${WORKDIR}" fetch origin main --prune
  git -C "${WORKDIR}" checkout main
  git -C "${WORKDIR}" reset --hard origin/main
  git -C "${WORKDIR}" clean -fdx
  git -C "${WORKDIR}" config user.name "${GIT_USER_NAME}"
  git -C "${WORKDIR}" config user.email "${GIT_USER_EMAIL}"
}

run_event_command() {
  local command="$1"
  local payload_file="${2:-}"
  local payload

  payload="$(event_payload_file "${payload_file}")" || return 0

  case "${command}" in
    issue-opened)
      review_new_issue_event "${payload}"
      ;;
    issue-commented)
      if [[ "$(jq -r 'has("issue") and (.issue.pull_request != null)' "${payload}")" == "true" ]]; then
        track_pr_event "${payload}" "issue-comment-on-pr" "true"
        review_tracked_pr_state "${PR_STATE_DIR}/pr-$(jq -r '.issue.number' "${payload}").json" "true"
      else
        reconcile_admission_requests
      fi
      ;;
    pr-opened|pr-reopened|pr-ready-for-review|pr-synchronized)
      track_pr_event "${payload}" "${command}" "false"
      ;;
    pr-commented|pr-review-submitted)
      track_pr_event "${payload}" "${command}" "true"
      review_tracked_pr_state "${PR_STATE_DIR}/pr-$(jq -r '.pull_request.number // .issue.number' "${payload}").json" "true"
      ;;
    checks-completed)
      reconcile_open_prs
      reconcile_orphan_open_prs
      reconcile_tracked_prs
      ;;
    pr-closed)
      reconcile_merged_prs
      reconcile_tracked_prs
      reconcile_legacy_closed_issues
      ;;
    *)
      log "unknown event command: ${command}"
      return 0
      ;;
  esac
}

run_issue() {
  local issue_json="$1"
  local number title labels body url branch slug prompt pr_url pr_number validation_summary existing_pr_json existing_pr_number existing_pr_url

  number="$(jq -r '.number' <<<"${issue_json}")"
  title="$(jq -r '.title' <<<"${issue_json}")"
  labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"
  body="$(jq -r '.body // ""' <<<"${issue_json}")"
  url="$(jq -r '.url // ""' <<<"${issue_json}")"

  if issue_has_label "${labels}" "${RUNNING_LABEL}" \
    || issue_has_label "${labels}" "${PR_LABEL}" \
    || issue_has_label "${labels}" "${WAITING_CHECKS_LABEL}" \
    || issue_has_label "${labels}" "${FAILING_CHECKS_LABEL}" \
    || issue_has_label "${labels}" "${UNDER_REVIEW_LABEL}" \
    || issue_has_label "${labels}" "${COVERAGE_GAP_LABEL}" \
    || issue_has_label "${labels}" "${APPROVED_LABEL}" \
    || issue_has_label "${labels}" "${FAILED_LABEL}" \
    || issue_has_label "${labels}" "${NEEDS_HUMAN_LABEL}" \
    || issue_has_label "${labels}" "${MERGED_LABEL}"; then
    if issue_has_label "${labels}" "${QUEUE_LABEL}"; then
      remove_label "${number}" "${QUEUE_LABEL}"
      remove_label "${number}" "${TRIGGER_LABEL}"
      log "cleaned queued admission labels for issue #${number}: already has automation lifecycle label"
    fi
    log "skipping issue #${number}: already has automation lifecycle label"
    return 0
  fi

  existing_pr_json="$(open_pr_for_issue "${number}")"
  if [[ -n "${existing_pr_json}" && "${existing_pr_json}" != "null" ]]; then
    existing_pr_number="$(jq -r '.number' <<<"${existing_pr_json}")"
    existing_pr_url="$(jq -r '.url' <<<"${existing_pr_json}")"
    mark_needs_human "${number}" "An open PR already exists for this issue: #${existing_pr_number} (${existing_pr_url}). Refusing to create duplicate autonomous work."
    return 0
  fi

  slug="$(safe_slug "${title}")"
  if [[ -z "${slug}" ]]; then
    slug="work"
  fi
  branch="scc/issue-${number}-${slug}"

  log "claiming issue #${number}: ${title}"
  write_heartbeat "running" "claiming issue #${number}"
  add_label "${number}" "${RUNNING_LABEL}"
  comment_issue "${number}" "Autonomous SDLC claimed this issue. Branch: \`${branch}\`. The worker will obey repository rules and will not use admin bypass."

  prepare_workdir
  git -C "${WORKDIR}" checkout -B "${branch}" origin/main

  # Fetch recent issue comments for context
  local issue_comments
  issue_comments="$(gh issue view "${number}" --repo "${REPO}" --json comments --jq '.comments[-5:] | map("- [" + (.author.login // "unknown") + " at " + .createdAt + "] " + .body) | join("\n")' 2>>"${LOGFILE}" || true)"

  prompt="$(cat <<EOF
Implement GitHub issue #${number} in ${REPO}.

Issue title:
${title}

Issue URL:
${url}

Issue body:
${body}
EOF
)"

  if [[ -n "${issue_comments}" && "${issue_comments}" != "null" ]]; then
    prompt+="$(cat <<EOF


Recent issue comments (newest last):
${issue_comments}
EOF
)"
  fi

  prompt+="$(cat <<EOF


You are an autonomous agent implementing this issue. Follow these rules:

1. SCOPE: Implement ONLY what is described in acceptance criteria. If multiple criteria exist, implement them in order, one at a time.

2. EXECUTION: Use tools (read_file, edit_file, write_file) to make changes immediately. Do not describe what you would do - execute now.

3. VALIDATION: Run the narrowest validation that proves your change works:
   - Java file changed: mvn test -Dtest=<ClassName>Test
   - Template changed: mvn test -Dtest=<Resource>Test
   - Config changed: syntax check only
   - Docs changed: markdownlint or no validation
   Include validation output in your response. Do not run full test suite - worker and CI handle comprehensive validation.

4. COMMITS: Leave files edited (do not commit). The worker creates commits automatically.

5. CONSTRAINTS:
   - Maximum 5 files changed (if issue needs more, comment and stop)
   - Maximum 100 lines per file (if more, comment and stop)
   - When faced with ambiguity, apply industry best practices and document your decision

6. BRANCH: You are on branch ${branch}. Never push to main. Never force push.

7. SAFETY:
   - Do not use --admin or bypass branch protection, checks, reviews, or rulesets
   - Do not change branch protection, rulesets, required checks, or secrets
   - If repository rules block merge, report blocker and stop

8. SCOPE DISCIPLINE: If you discover additional issues outside this issue's scope, comment on the issue noting them for separate tracking. Do not expand scope.

9. PULL REQUEST: Ensure PR body contains ## Issue Coverage section mapping code changes to acceptance criteria. Do not mark items complete unless code/tests actually satisfy them.

10. AUTONOMOUS DECISION-MAKING: When faced with implementation choices:
   - FOLLOW codebase patterns (check existing code for naming, structure, style)
   - APPLY best practices (DRY, SOLID, security-first, performance-aware)
   - PREFER incremental changes over big-bang rewrites
   - CHOOSE reversible approaches (can rollback if needed)
   - DOCUMENT your reasoning in commit messages for complex decisions

   Examples of automatic decisions (DO NOT escalate):
   - Code style → Follow existing patterns in codebase
   - Performance optimization → Apply safe optimizations (consolidate files, remove duplicates, add caching)
   - Error handling → Always add appropriate error handling
   - Naming → Use descriptive names following codebase conventions
   - Refactoring → Prefer async/await over callbacks, extract reusable functions
   - Tests → Add tests following existing test patterns
   - Dependencies → Update if safe (check CHANGELOG for breaking changes)

   ONLY stop if:
   - Decision requires business/product judgment (not technical)
   - Multiple approaches are technically equivalent with different business tradeoffs
   - High risk of data loss or security breach if wrong
   - Fundamental architecture change affecting system design

   When you make an autonomous decision, document it:
   ## Autonomous Decisions
   - [Decision]: Brief description
   - [Rationale]: Why this approach was chosen
   - [Pattern]: What codebase pattern or best practice was followed
   - [Reversible]: Yes/No and rollback approach if applicable

Begin implementation now. Use tools, do not narrate.
EOF
)"

  log "running SCC for issue #${number}"
  write_heartbeat "running" "SCC running for issue #${number}"
  local scc_rc
  if run_scc_checked "${prompt}" "${body}"; then
    :
  else
    scc_rc=$?
    log "SCC failed for issue #${number}"
    if [[ "${scc_rc}" -eq 124 ]]; then
      local issue_complexity
      issue_complexity=$(classify_issue_complexity "${body}")
      local actual_timeout
      actual_timeout=$(get_timeout_for_complexity "${issue_complexity}")
      mark_failed "${number}" "SCC timed out after ${actual_timeout}s (complexity: ${issue_complexity}). Check ${LOGFILE} on the runner."
      return 0
    fi
    mark_failed "${number}" "SCC exited non-zero (${scc_rc}). Check ${LOGFILE} on the runner."
    return 0
  fi

  if [[ "$(git -C "${WORKDIR}" branch --show-current)" == "main" ]]; then
    mark_needs_human "${number}" "SCC ended on main. Refusing to continue because autonomous work must use a PR branch."
    return 0
  fi

  if [[ -n "$(git -C "${WORKDIR}" status --porcelain)" ]]; then
    # ADEV Rule #11: Run narrowest validation before commit
    local changed_files
    changed_files=$( (git -C "${WORKDIR}" diff --name-only HEAD && git -C "${WORKDIR}" ls-files --others --exclude-standard) 2>/dev/null || echo "")
    local scoped_validation_cmd
    scoped_validation_cmd=$(get_validation_command_for_changes "$changed_files")

    if [[ -n "${scoped_validation_cmd}" ]]; then
      log "Running scoped validation: ${scoped_validation_cmd}"
      if (cd "${WORKDIR}" && bash -c "${scoped_validation_cmd}"); then
        log "Scoped validation passed"
      else
        mark_failed "${number}" "Scoped validation failed: ${scoped_validation_cmd}"
        return 0
      fi
    else
      log "No scoped validation defined for changed files; relying on CI"
    fi

    log "committing SCC changes for issue #${number}"
    git -C "${WORKDIR}" add -A
    git -C "${WORKDIR}" commit -m "chore(sdlc): implement issue #${number}" -m "Refs #${number}"
  fi

  if [[ -z "$(git -C "${WORKDIR}" log --oneline "origin/main..HEAD")" ]]; then
    local diagnostic_msg="SCC completed without producing any branch changes. Common causes:
- Agent responded with intent (\"Now I'll...\") but did not execute tools in batch mode
- Issue description may be ambiguous or under-specified
- Agent did not have sufficient permission or context to proceed

Recommendation: Review the issue description for clarity, check SCC logs, or verify SCC autonomous mode configuration."
    mark_needs_human "${number}" "${diagnostic_msg}"
    return 0
  fi

  validation_summary="Worker validation command not configured; GitHub checks are required before approval."
  if [[ -n "${VALIDATION_COMMAND}" ]]; then
    log "running validation for issue #${number}: ${VALIDATION_COMMAND}"
    if (cd "${WORKDIR}" && bash -lc "${VALIDATION_COMMAND}"); then
      validation_summary="\`${VALIDATION_COMMAND}\` passed"
    else
      mark_failed "${number}" "Validation command failed: \`${VALIDATION_COMMAND}\`."
      return 0
    fi
  fi

  if ! git -C "${WORKDIR}" push -u origin "${branch}" 2>/dev/null; then
    mark_failed "${number}" "Git push failed for branch ${branch}."
    return 0
  fi

  pr_url="$(gh pr view "${branch}" --repo "${REPO}" --template '{{.url}}' 2>/dev/null || true)"
  if [[ -z "${pr_url}" ]]; then
    if ! pr_url="$(gh pr create \
      --repo "${REPO}" \
      --base main \
      --head "${branch}" \
      --title "chore(sdlc): implement issue #${number}" \
      --body "$(cat <<PRBODY
## Summary

Autonomous SCC implementation for issue #${number}: ${title}

## Validation

${validation_summary}

## Issue Coverage

- [ ] Map concrete code changes to issue #${number}: ${title}
- [ ] Map each acceptance criterion, or explain why none applies.
- [ ] List any known uncovered requirement, or state that none is known with evidence.

## Governance

- Branch protection, required checks, required reviews, and repository rules still apply.
- No admin bypass was used.

Refs #${number}
PRBODY
)" 2>/dev/null)"; then
      mark_failed "${number}" "GitHub PR creation failed for branch ${branch}."
      return 0
    fi
  fi

  set_flow_labels "${number}" "${PR_LABEL}" "${WAITING_CHECKS_LABEL}"
  write_issue_state "${number}" "${branch}" "${pr_url}"
  pr_number="$(sed -nE 's#.*/pull/([0-9]+).*#\1#p' <<<"${pr_url}" | head -n1)"
  append_run_summary "${number}" "pr-opened" "${pr_number}" "${branch}" "SCC opened or updated ${pr_url}. Validation: ${validation_summary}"
  comment_issue "${number}" "Autonomous SDLC opened PR: ${pr_url}"
  comment_issue "${number}" "Autonomous SDLC is waiting for checks and review on ${pr_url}. Auto-merge will only be enabled after the worker marks the PR as \`${APPROVED_LABEL}\`; repository rules still apply."
  write_heartbeat "ok" "opened PR for issue #${number}"
}

main() {
  local command="${1:-reconcile}"
  local payload_file="${2:-}"

  write_heartbeat "starting" "worker starting"
  require_cmd gh
  require_cmd git
  require_cmd python3
  require_cmd jq
  require_cmd "${SCC_BIN}"
  require_gh_auth

  exec 9>"${LOCK_FILE}"
  if ! flock -n 9; then
    log "another worker instance is already running"
    write_heartbeat "skipped" "another worker instance is already running"
    exit 0
  fi

  if [[ "${command}" != "reconcile" ]]; then
    log "handling SDLC event command=${command}"
    write_heartbeat "running" "handling event ${command}"
    run_event_command "${command}" "${payload_file}"
    write_heartbeat "ok" "event ${command} complete"
    exit 0
  fi

  log "reconciling merged autonomous SDLC PRs"
  write_heartbeat "running" "reconciling merged PRs"
  reconcile_merged_prs
  reconcile_legacy_closed_issues
  reconcile_open_prs
  reconcile_orphan_open_prs
  reconcile_tracked_prs

  log "checking eligible issues in ${REPO}"
  write_heartbeat "running" "checking eligible issues"
  reconcile_stuck_admission_reviews
  reconcile_admission_requests
  mapfile -t issues < <(
    gh issue list \
      --repo "${REPO}" \
      --state open \
      --label "${QUEUE_LABEL}" \
      --limit "${MAX_ISSUES}" \
      --json number,title,body,url,author,labels,createdAt
  )

  if [[ "${#issues[@]}" -eq 0 || -z "${issues[0]:-}" || "${issues[0]}" == "[]" ]]; then
    log "no eligible issues found"
    write_heartbeat "ok" "no eligible issues found"
    exit 0
  fi

  # Sort by createdAt (oldest first) and take only the first one (FIFO queue, one at a time)
  local sorted_issues
  sorted_issues="$(jq 'sort_by(.createdAt) | .[0:1]' <<<"${issues[0]}")"

  local issue_numbers
  issue_numbers="$(jq -r '.[].number' <<<"${sorted_issues}" | tr '\n' ',' | sed 's/,$//')"
  log "main: processing oldest eligible issue (FIFO): ${issue_numbers}"

  # Use process substitution instead of pipe to avoid subshell issues
  while IFS= read -r issue_json; do
    run_issue "${issue_json}"
  done < <(jq -c '.[]' <<<"${sorted_issues}")
  write_heartbeat "ok" "cycle complete"
}

main "$@"
