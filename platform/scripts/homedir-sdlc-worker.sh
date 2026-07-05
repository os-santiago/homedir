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
RUNNING_LABEL="${HOMEDIR_SDLC_RUNNING_LABEL:-scc-running}"
PR_LABEL="${HOMEDIR_SDLC_PR_LABEL:-scc-pr-open}"
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
VALIDATION_COMMAND="${HOMEDIR_SDLC_VALIDATION_COMMAND:-}"
GIT_USER_NAME="${HOMEDIR_SDLC_GIT_USER_NAME:-homedir-sdlc[bot]}"
GIT_USER_EMAIL="${HOMEDIR_SDLC_GIT_USER_EMAIL:-homedir-sdlc@users.noreply.github.com}"
SCC_BIN="${SCC_BIN:-/usr/local/bin/scc}"
LOCK_FILE="${STATE_DIR}/worker.lock"
ISSUE_STATE_DIR="${STATE_DIR}/issues"
RUN_SUMMARY_DIR="${STATE_DIR}/run-summaries"
HEARTBEAT_FILE="${HOMEDIR_SDLC_HEARTBEAT_FILE:-${STATE_DIR}/heartbeat.json}"
MAX_REMEDIATION_ATTEMPTS="${HOMEDIR_SDLC_MAX_REMEDIATION_ATTEMPTS:-5}"
ALERTS_ENABLED="${HOMEDIR_SDLC_ALERTS_ENABLED:-false}"
ALERT_WEBHOOK_URL="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL:-}"
ALERT_WEBHOOK_URL_FILE="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL_FILE:-}"
ALERT_TIMEOUT_SECONDS="${HOMEDIR_SDLC_ALERT_TIMEOUT_SECONDS:-10}"

mkdir -p "${STATE_DIR}" "${ISSUE_STATE_DIR}" "${RUN_SUMMARY_DIR}" "$(dirname "${LOGFILE}")"
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

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 1
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
  gh pr list \
    --repo "${REPO}" \
    --state open \
    --search "${issue} in:title,body" \
    --limit 20 \
    --json number,title,url \
    --jq '.[0] // empty' \
    2>/dev/null
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
    return 0
  fi

  jq -c '.[]' <<<"${issues_json}" | while IFS= read -r issue_json; do
    number="$(jq -r '.number' <<<"${issue_json}")"
    labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"

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
      continue
    fi

    labeler="$(latest_trigger_labeler "${number}")"
    if is_authorized_labeler "${labeler}"; then
      admit_issue_to_queue "${number}" "${labeler}"
    else
      reject_issue_from_queue "${number}" "${labeler}"
    fi
  done
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

coverage_match = re.search(r"(?ims)^##\s+Issue Coverage\s*$([\s\S]*?)(?=^##\s+|\Z)", pr)
coverage = coverage_match.group(1).strip() if coverage_match else ""

if not coverage_match:
    gaps.append("PR body is missing a `## Issue Coverage` section.")
elif re.search(r"(?m)^\s*[-*]\s+\[\s\]", coverage):
    gaps.append("`## Issue Coverage` still contains unchecked items.")
else:
    coverage_items = re.findall(r"(?m)^\s*[-*]\s+\[[xX]\]\s+(.+?)\s*$", coverage)
    generic_patterns = [
        r"^implements the requested issue scope",
        r"^maps acceptance criteria and technical observations",
        r"^leaves no known issue requirement",
        r"^source issue requirements reviewed",
    ]
    specific_items = [
        item for item in coverage_items
        if not any(re.search(pattern, item, re.I) for pattern in generic_patterns)
    ]
    if not specific_items:
        gaps.append("`## Issue Coverage` only contains generic boilerplate; add issue-specific coverage evidence.")

has_acceptance = bool(
    re.search(r"(?im)acceptance criteria|criterios de aceptaci[o\u00f3]n|definici[o\u00f3]n de list[oa]s?", issue)
    or re.search(r"(?m)^\s*[-*]\s+\[\s\]", issue)
)
if has_acceptance and not re.search(r"(?im)acceptance criteria|criterios de aceptaci[o\u00f3]n|definici[o\u00f3]n de list[oa]s?", pr):
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
- If the trigger is a coverage gap, update the implementation and PR body so `## Issue Coverage` truthfully maps code changes to the issue request and acceptance criteria.
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
  if ! (cd "${WORKDIR}" && "${SCC_BIN}" -yq "${prompt}"); then
    mark_failed "${issue}" "SCC remediation exited non-zero for PR #${pr_number}. Check ${LOGFILE} on the runner."
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
      set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}"
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

  if [[ "${ENABLE_AUTOMERGE}" != "true" ]]; then
    return 1
  fi

  if gh pr merge "${branch}" --repo "${REPO}" --squash --auto >/dev/null 2>&1; then
    log "enabled normal auto-merge for issue #${issue} (branch=${branch} pr=#${pr_number})"
    return 0
  fi

  log "auto-merge not available for issue #${issue} (branch=${branch} pr=#${pr_number})"
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

reconcile_pr_state() {
  local state_file="$1"
  local issue pr_number branch pr_json pr_state pr_url pr_title pr_sha is_draft checks_json reviews_json
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
    run_scc_on_existing_pr "${issue}" "${pr_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${reviews_json}" "${trigger}"
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
    run_scc_on_existing_pr "${issue}" "${pr_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${reviews_json}" "${trigger}"
    return 0
  fi

  if [[ "${coverage_passed}" != "true" ]]; then
    trigger="technical issue coverage gap on PR #${pr_number}: $(jq -r '.gaps | join("; ")' <<<"${coverage_json}")"
    set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}"
    update_issue_state "${issue}" '.last_pr_state = "coverage-gap" | .last_checked_at = $updated_at'
    run_scc_on_existing_pr "${issue}" "${pr_title}" "${branch}" "${pr_number}" "${pr_url}" "${checks_json}" "${coverage_json}" "${trigger}"
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
    jq -c '.[]' <<<"${issues_json}" | while IFS= read -r issue_json; do
      reconcile_legacy_closed_issue "${issue_json}"
    done
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

  prompt="$(cat <<EOF
Implement GitHub issue #${number} in ${REPO}.

Issue title:
${title}

Issue URL:
${url}

Issue body:
${body}

Rules:
- Work only within the issue scope.
- Use branch ${branch}; never push directly to main.
- Make a focused implementation, then leave a concise summary of validation.
- Ensure the pull request body contains a `## Issue Coverage` section that maps code changes to the issue request and acceptance criteria.
- Do not mark coverage items complete unless the code or tests in the PR actually satisfy them.
- It is okay to edit files without committing; the worker will create the final commit if needed.
- Run the smallest meaningful local validation and include evidence in the PR.
- Do not use --admin.
- Do not bypass branch protection, required checks, reviews, or repository rulesets.
- Do not change branch protection, repository rulesets, required status checks, or secrets.
- If repository rules block merge, report the blocker and stop.
- If requirements are ambiguous or unsafe, add a clear issue comment and stop.
EOF
)"

  log "running SCC for issue #${number}"
  write_heartbeat "running" "SCC running for issue #${number}"
  if ! (cd "${WORKDIR}" && "${SCC_BIN}" -yq "${prompt}"); then
    log "SCC failed for issue #${number}"
    mark_failed "${number}" "SCC exited non-zero. Check ${LOGFILE} on the runner."
    return 0
  fi

  if [[ "$(git -C "${WORKDIR}" branch --show-current)" == "main" ]]; then
    mark_needs_human "${number}" "SCC ended on main. Refusing to continue because autonomous work must use a PR branch."
    return 0
  fi

  if [[ -n "$(git -C "${WORKDIR}" status --porcelain)" ]]; then
    log "committing SCC changes for issue #${number}"
    git -C "${WORKDIR}" add -A
    git -C "${WORKDIR}" commit -m "chore(sdlc): implement issue #${number}" -m "Refs #${number}"
  fi

  if [[ -z "$(git -C "${WORKDIR}" log --oneline "origin/main..HEAD")" ]]; then
    mark_needs_human "${number}" "SCC completed without producing any branch changes."
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

- [x] Addresses issue #${number}: ${title}
- [x] Implements the requested issue scope for #${number}.
- [x] Maps acceptance criteria and technical observations from the issue body to the PR changes.
- [x] Leaves no known issue requirement intentionally uncovered.

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

  log "reconciling merged autonomous SDLC PRs"
  write_heartbeat "running" "reconciling merged PRs"
  reconcile_merged_prs
  reconcile_legacy_closed_issues
  reconcile_open_prs

  log "checking eligible issues in ${REPO}"
  write_heartbeat "running" "checking eligible issues"
  reconcile_admission_requests
  mapfile -t issues < <(
    gh issue list \
      --repo "${REPO}" \
      --state open \
      --label "${QUEUE_LABEL}" \
      --limit "${MAX_ISSUES}" \
      --json number,title,body,url,author,labels
  )

  if [[ "${#issues[@]}" -eq 0 || -z "${issues[0]:-}" || "${issues[0]}" == "[]" ]]; then
    log "no eligible issues found"
    write_heartbeat "ok" "no eligible issues found"
    exit 0
  fi

  jq -c '.[]' <<<"${issues[0]}" | while IFS= read -r issue_json; do
    run_issue "${issue_json}"
  done
  write_heartbeat "ok" "cycle complete"
}

main "$@"
