#!/usr/bin/env bash
# Issue-driven SCC worker for the HomeDir autonomous SDLC.

set -euo pipefail

ENV_FILE="${HOMEDIR_SDLC_ENV_FILE:-/etc/homedir-sdlc.env}"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
AUTHOR="${HOMEDIR_SDLC_AUTHOR:-scanalesespinoza}"
TRIGGER_LABEL="${HOMEDIR_SDLC_TRIGGER_LABEL:-ready-to-implement}"
RUNNING_LABEL="${HOMEDIR_SDLC_RUNNING_LABEL:-scc-running}"
PR_LABEL="${HOMEDIR_SDLC_PR_LABEL:-scc-pr-open}"
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
HEARTBEAT_FILE="${HOMEDIR_SDLC_HEARTBEAT_FILE:-${STATE_DIR}/heartbeat.json}"
ALERTS_ENABLED="${HOMEDIR_SDLC_ALERTS_ENABLED:-false}"
ALERT_WEBHOOK_URL="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL:-}"
ALERT_WEBHOOK_URL_FILE="${HOMEDIR_SDLC_ALERT_WEBHOOK_URL_FILE:-}"
ALERT_TIMEOUT_SECONDS="${HOMEDIR_SDLC_ALERT_TIMEOUT_SECONDS:-10}"

mkdir -p "${STATE_DIR}" "${ISSUE_STATE_DIR}" "$(dirname "${LOGFILE}")"
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
  comment_issue "${issue}" "Autonomous SDLC paused: ${reason}"
  alert WARN "Issue #${issue} needs human review" "${reason}"
}

mark_failed() {
  local issue="$1"
  local reason="$2"
  add_label "${issue}" "${FAILED_LABEL}"
  remove_label "${issue}" "${RUNNING_LABEL}"
  comment_issue "${issue}" "Autonomous SDLC failed: ${reason}"
  alert FAIL "Issue #${issue} failed" "${reason}"
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

release_status_for_pr() {
  local pr_number="$1"
  local pr_json merge_sha runs_json run_status run_conclusion run_url

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
    echo "success|Production Release succeeded|${run_url}"
  elif [[ "${run_status}" == "completed" ]]; then
    echo "failure|Production Release completed with conclusion: ${run_conclusion}|${run_url}"
  else
    echo "pending|Production Release is ${run_status}|${run_url}"
  fi
}

try_enable_auto_merge() {
  local issue="$1"
  local branch="$2"
  local pr_number="${3:-}"
  local pr_url="${4:-}"

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

reconcile_open_prs() {
  local state_file

  while IFS= read -r state_file; do
    enable_auto_merge_for_state "${state_file}"
  done < <(find "${ISSUE_STATE_DIR}" -maxdepth 1 -name 'issue-*.json' -type f 2>/dev/null)
}

reconcile_completed_issue() {
  local issue_json="$1"
  local number labels issue_detail prs_json pr_number pr_url release_status release_message release_url

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
    log "closed issue #${number} has ${PR_LABEL} but no closing PR reference"
    return 0
  fi

  pr_number="$(jq -r '.[0].number' <<<"${prs_json}")"
  pr_url="$(jq -r '.[0].url' <<<"${prs_json}")"

  IFS='|' read -r release_status release_message release_url < <(release_status_for_pr "${pr_number}")

  if [[ "${release_status}" == "pending" ]]; then
    log "issue #${number} PR #${pr_number} merged; waiting for release verification: ${release_message}"
    return 0
  fi

  if [[ "${release_status}" == "failure" ]]; then
    add_label "${number}" "${NEEDS_HUMAN_LABEL}"
    comment_issue "${number}" "Autonomous SDLC merge completed, but release verification failed for PR #${pr_number}: ${release_message} ${release_url}"
    log "issue #${number} release verification failed for PR #${pr_number}"
    alert FAIL "Issue #${number} release failed" "PR #${pr_number}: ${release_message} ${release_url}"
    return 0
  fi

  add_label "${number}" "${MERGED_LABEL}"
  remove_label "${number}" "${PR_LABEL}"
  remove_label "${number}" "${RUNNING_LABEL}"
  remove_label "${number}" "${FAILED_LABEL}"
  remove_label "${number}" "${NEEDS_HUMAN_LABEL}"
  remove_label "${number}" "${TRIGGER_LABEL}"
  comment_issue "${number}" "Autonomous SDLC completed: PR #${pr_number} was merged (${pr_url}) and release verification succeeded. ${release_url}"
  log "reconciled completed issue #${number} via PR #${pr_number}; release verified"
  alert INFO "Issue #${number} deployed" "PR #${pr_number} was merged and Production Release succeeded. ${release_url}"
}

reconcile_completed_issues() {
  local issues_json issue_json label

  issues_json="$(
    for label in "${PR_LABEL}" "${RUNNING_LABEL}" "${NEEDS_HUMAN_LABEL}" "${FAILED_LABEL}" "${TRIGGER_LABEL}"; do
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
      if issue_has_label "$(jq -c '[.labels[].name]' <<<"${issue_json}")" "${PR_LABEL}"; then
        reconcile_completed_issue "${issue_json}"
      else
        local number labels
        number="$(jq -r '.number' <<<"${issue_json}")"
        labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"
        if issue_has_label "${labels}" "${MERGED_LABEL}"; then
          continue
        fi
        log "closed issue #${number} has autonomous labels but no ${PR_LABEL}; cleaning terminal labels"
        remove_label "${number}" "${RUNNING_LABEL}"
        remove_label "${number}" "${FAILED_LABEL}"
        remove_label "${number}" "${NEEDS_HUMAN_LABEL}"
        remove_label "${number}" "${TRIGGER_LABEL}"
      fi
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
  local number title author labels body url branch slug prompt pr_url validation_summary

  number="$(jq -r '.number' <<<"${issue_json}")"
  title="$(jq -r '.title' <<<"${issue_json}")"
  author="$(jq -r '.author.login' <<<"${issue_json}")"
  labels="$(jq -c '[.labels[].name]' <<<"${issue_json}")"
  body="$(jq -r '.body // ""' <<<"${issue_json}")"
  url="$(jq -r '.url // ""' <<<"${issue_json}")"

  if [[ "${author}" != "${AUTHOR}" ]]; then
    log "skipping issue #${number}: author ${author} is not ${AUTHOR}"
    return 0
  fi

  if issue_has_label "${labels}" "${RUNNING_LABEL}" \
    || issue_has_label "${labels}" "${PR_LABEL}" \
    || issue_has_label "${labels}" "${FAILED_LABEL}" \
    || issue_has_label "${labels}" "${NEEDS_HUMAN_LABEL}" \
    || issue_has_label "${labels}" "${MERGED_LABEL}"; then
    log "skipping issue #${number}: already has automation lifecycle label"
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
    git -C "${WORKDIR}" commit -m "chore(sdlc): implement issue #${number}" -m "Closes #${number}"
  fi

  if [[ -z "$(git -C "${WORKDIR}" log --oneline "origin/main..HEAD")" ]]; then
    mark_needs_human "${number}" "SCC completed without producing any branch changes."
    return 0
  fi

  validation_summary="Not run by worker"
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

## Governance

- Branch protection, required checks, required reviews, and repository rules still apply.
- No admin bypass was used.

Closes #${number}
PRBODY
)" 2>/dev/null)"; then
      mark_failed "${number}" "GitHub PR creation failed for branch ${branch}."
      return 0
    fi
  fi

  add_label "${number}" "${PR_LABEL}"
  remove_label "${number}" "${RUNNING_LABEL}"
  write_issue_state "${number}" "${branch}" "${pr_url}"
  comment_issue "${number}" "Autonomous SDLC opened PR: ${pr_url}"

  if try_enable_auto_merge "${number}" "${branch}" "" "${pr_url}"; then
    comment_issue "${number}" "Normal GitHub auto-merge was enabled for ${pr_url}. Required checks and reviews still apply."
  else
    comment_issue "${number}" "Auto-merge is disabled or not available for the autonomous SDLC. PR remains governed by normal review and branch protection."
  fi
  write_heartbeat "ok" "opened PR for issue #${number}"
}

main() {
  write_heartbeat "starting" "worker starting"
  require_cmd gh
  require_cmd git
  require_cmd jq
  require_cmd "${SCC_BIN}"
  require_gh_auth

  exec 9>"${LOCK_FILE}"
  if ! flock -n 9; then
    log "another worker instance is already running"
    write_heartbeat "skipped" "another worker instance is already running"
    exit 0
  fi

  log "reconciling completed autonomous SDLC issues"
  write_heartbeat "running" "reconciling completed issues"
  reconcile_completed_issues
  reconcile_open_prs

  log "checking eligible issues in ${REPO}"
  write_heartbeat "running" "checking eligible issues"
  mapfile -t issues < <(
    gh issue list \
      --repo "${REPO}" \
      --state open \
      --label "${TRIGGER_LABEL}" \
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
