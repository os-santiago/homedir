#!/bin/bash
# SDLC E2E Test Orchestrator
# Executes full end-to-end tests of the AI SDLC pipeline from issue creation to production

set -euo pipefail

# Configuration
REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TEST_LABEL="e2e-test"
TIMEOUT_ADMISSION="${E2E_TIMEOUT_ADMISSION:-60}"        # 1 minute
TIMEOUT_QUEUE="${E2E_TIMEOUT_QUEUE:-180}"              # 3 minutes
TIMEOUT_RUNNING="${E2E_TIMEOUT_RUNNING:-1800}"         # 30 minutes
TIMEOUT_PR_CREATION="${E2E_TIMEOUT_PR_CREATION:-120}"  # 2 minutes
TIMEOUT_CHECKS="${E2E_TIMEOUT_CHECKS:-600}"            # 10 minutes
TIMEOUT_MERGE="${E2E_TIMEOUT_MERGE:-300}"              # 5 minutes
TIMEOUT_DEPLOYMENT="${E2E_TIMEOUT_DEPLOYMENT:-600}"    # 10 minutes

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# State
TEST_START_TIME=""
TEST_ISSUE=""
TEST_PR=""
PHASE_TIMINGS=()

# Logging
log() {
  echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $*"
}

success() {
  echo -e "${GREEN}✓${NC} $*"
}

error() {
  echo -e "${RED}✗${NC} $*" >&2
}

warn() {
  echo -e "${YELLOW}⚠${NC} $*"
}

phase() {
  local phase_name="$1"
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}  PHASE: ${phase_name}${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""
}

record_timing() {
  local phase="$1"
  local duration="$2"
  PHASE_TIMINGS+=("${phase}:${duration}s")
  log "Phase '${phase}' completed in ${duration}s"
}

elapsed_seconds() {
  local start="${1}"
  local now
  now=$(date +%s)
  echo $((now - start))
}

wait_for_label() {
  local issue="$1"
  local expected_label="$2"
  local timeout="$3"
  local start
  start=$(date +%s)

  log "Waiting for label '${expected_label}' on issue #${issue} (timeout: ${timeout}s)..."

  while true; do
    local labels
    labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',' || true)

    if echo "${labels}" | grep -q "${expected_label}"; then
      local elapsed
      elapsed=$(elapsed_seconds "${start}")
      success "Label '${expected_label}' found after ${elapsed}s"
      echo "${elapsed}"
      return 0
    fi

    # Check for failure labels
    if echo "${labels}" | grep -qE "scc-failed|scc-rejected|needs-human"; then
      error "Issue entered failure state: ${labels}"
      return 1
    fi

    local elapsed
    elapsed=$(elapsed_seconds "${start}")
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout waiting for label '${expected_label}' (${timeout}s elapsed)"
      error "Current labels: ${labels}"
      return 1
    fi

    sleep 5
  done
}

wait_for_pr() {
  local issue="$1"
  local timeout="$2"
  local start
  start=$(date +%s)

  log "Waiting for PR creation for issue #${issue} (timeout: ${timeout}s)..."

  while true; do
    local pr_json
    pr_json=$(gh pr list -R "${REPO}" --search "in:title closes #${issue}" --json number,state --limit 1 2>/dev/null || echo "[]")

    if [[ "${pr_json}" != "[]" ]]; then
      local pr_number
      pr_number=$(echo "${pr_json}" | jq -r '.[0].number // empty')

      if [[ -n "${pr_number}" ]]; then
        local elapsed
        elapsed=$(elapsed_seconds "${start}")
        success "PR #${pr_number} created after ${elapsed}s"
        echo "${pr_number}"
        return 0
      fi
    fi

    local elapsed
    elapsed=$(elapsed_seconds "${start}")
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout waiting for PR creation (${timeout}s elapsed)"
      return 1
    fi

    sleep 10
  done
}

wait_for_checks() {
  local pr="$1"
  local timeout="$2"
  local start
  start=$(date +%s)

  log "Waiting for checks to complete on PR #${pr} (timeout: ${timeout}s)..."

  while true; do
    local checks_json
    checks_json=$(gh pr view "${pr}" -R "${REPO}" --json statusCheckRollup 2>/dev/null || echo '{"statusCheckRollup":[]}')

    local total_checks
    total_checks=$(echo "${checks_json}" | jq -r '.statusCheckRollup | length')

    if [[ ${total_checks} -eq 0 ]]; then
      sleep 10
      continue
    fi

    local pending
    pending=$(echo "${checks_json}" | jq -r '[.statusCheckRollup[] | select(.status == "PENDING" or .status == "IN_PROGRESS")] | length')

    local failed
    failed=$(echo "${checks_json}" | jq -r '[.statusCheckRollup[] | select(.conclusion == "FAILURE" or .conclusion == "CANCELLED")] | length')

    if [[ ${failed} -gt 0 ]]; then
      warn "${failed} check(s) failed on PR #${pr}"
      echo "${checks_json}" | jq -r '.statusCheckRollup[] | select(.conclusion == "FAILURE") | "  - \(.name): \(.conclusion)"'
      # Don't return error immediately - worker should remediate
      log "Worker should attempt remediation..."
    fi

    if [[ ${pending} -eq 0 ]]; then
      local elapsed
      elapsed=$(elapsed_seconds "${start}")
      success "All checks completed after ${elapsed}s"
      echo "${elapsed}"
      return 0
    fi

    log "Checks status: ${pending}/${total_checks} pending..."

    local elapsed
    elapsed=$(elapsed_seconds "${start}")
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout waiting for checks (${timeout}s elapsed)"
      return 1
    fi

    sleep 15
  done
}

wait_for_merge() {
  local pr="$1"
  local timeout="$2"
  local start
  start=$(date +%s)

  log "Waiting for PR #${pr} to merge (timeout: ${timeout}s)..."

  while true; do
    local pr_state
    pr_state=$(gh pr view "${pr}" -R "${REPO}" --json state --jq '.state')

    if [[ "${pr_state}" == "MERGED" ]]; then
      local elapsed
      elapsed=$(elapsed_seconds "${start}")
      success "PR #${pr} merged after ${elapsed}s"
      echo "${elapsed}"
      return 0
    fi

    if [[ "${pr_state}" == "CLOSED" ]]; then
      error "PR #${pr} was closed without merging"
      return 1
    fi

    local elapsed
    elapsed=$(elapsed_seconds "${start}")
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout waiting for PR merge (${timeout}s elapsed)"
      return 1
    fi

    sleep 10
  done
}

verify_deployment() {
  local pr="$1"
  local timeout="$2"

  log "Verifying deployment for PR #${pr}..."

  # Get the merge commit SHA
  local merge_sha
  merge_sha=$(gh pr view "${pr}" -R "${REPO}" --json mergeCommit --jq '.mergeCommit.oid')

  if [[ -z "${merge_sha}" ]]; then
    warn "Could not determine merge commit SHA"
    return 0
  fi

  log "Merge commit: ${merge_sha}"

  # Check for deployment workflow run
  local start
  start=$(date +%s)

  while true; do
    local runs_json
    runs_json=$(gh run list -R "${REPO}" --workflow="production-release.yml" --json databaseId,status,conclusion,headSha --limit 5 2>/dev/null || echo "[]")

    local deployment_run
    deployment_run=$(echo "${runs_json}" | jq -r ".[] | select(.headSha == \"${merge_sha}\") | .databaseId" | head -1)

    if [[ -n "${deployment_run}" ]]; then
      local conclusion
      conclusion=$(echo "${runs_json}" | jq -r ".[] | select(.databaseId == ${deployment_run}) | .conclusion")

      if [[ "${conclusion}" == "success" ]]; then
        success "Deployment workflow completed successfully"
        return 0
      elif [[ "${conclusion}" == "failure" ]]; then
        error "Deployment workflow failed"
        return 1
      else
        log "Deployment workflow in progress (status: ${conclusion:-pending})..."
      fi
    fi

    local elapsed
    elapsed=$(elapsed_seconds "${start}")
    if [[ ${elapsed} -ge ${timeout} ]]; then
      warn "Deployment verification timeout (${timeout}s) - may still be running"
      return 0
    fi

    sleep 15
  done
}

create_test_issue() {
  local test_type="$1"
  local title="$2"
  local body="$3"

  phase "CREATING TEST ISSUE"

  log "Creating ${test_type} test issue..."

  local issue_body
  issue_body=$(cat <<EOF
${body}

---

**E2E Test Metadata**
- Test Type: ${test_type}
- Test Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)
- Orchestrator: sdlc-e2e-orchestrator.sh
EOF
)

  local issue_number
  issue_number=$(gh issue create \
    -R "${REPO}" \
    --title "${title}" \
    --body "${issue_body}" \
    --label "${TEST_LABEL}" \
    --assignee "@me" \
    --json number \
    --jq '.number')

  if [[ -z "${issue_number}" ]]; then
    error "Failed to create test issue"
    return 1
  fi

  success "Created issue #${issue_number}"
  TEST_ISSUE="${issue_number}"
  echo "${issue_number}"
}

trigger_admission() {
  local issue="$1"

  phase "TRIGGERING ADMISSION"

  log "Adding 'ready-to-implement' label to issue #${issue}..."
  gh issue edit "${issue}" -R "${REPO}" --add-label "ready-to-implement"

  success "Admission triggered"
}

run_e2e_test() {
  local test_type="$1"
  local title="$2"
  local body="$3"

  TEST_START_TIME=$(date +%s)

  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║                 SDLC E2E TEST EXECUTION                        ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Test Type: ${test_type}${NC}"
  echo -e "${GREEN}║ Repository: ${REPO}${NC}"
  echo -e "${GREEN}║ Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  # Step 1: Create issue
  if ! TEST_ISSUE=$(create_test_issue "${test_type}" "${title}" "${body}"); then
    error "Failed to create test issue"
    return 1
  fi

  # Step 2: Trigger admission
  trigger_admission "${TEST_ISSUE}"

  # Step 3: Wait for admission acceptance
  phase "PHASE 1: ADMISSION REVIEW"
  local admission_time
  if ! admission_time=$(wait_for_label "${TEST_ISSUE}" "scc-accepted" "${TIMEOUT_ADMISSION}"); then
    error "Admission phase failed"
    return 1
  fi
  record_timing "admission" "${admission_time}"

  # Step 4: Wait for queue
  phase "PHASE 2: QUEUE ADMISSION"
  local queue_time
  if ! queue_time=$(wait_for_label "${TEST_ISSUE}" "scc-queued" "${TIMEOUT_QUEUE}"); then
    error "Queue admission failed"
    return 1
  fi
  record_timing "queue" "${queue_time}"

  # Step 5: Wait for worker to claim
  phase "PHASE 3: WORKER PROCESSING"
  local running_time
  if ! running_time=$(wait_for_label "${TEST_ISSUE}" "scc-running" "${TIMEOUT_RUNNING}"); then
    error "Worker processing failed"
    return 1
  fi
  record_timing "worker_claim" "${running_time}"

  # Step 6: Wait for PR creation
  phase "PHASE 4: PR CREATION"
  local pr_number
  if ! pr_number=$(wait_for_pr "${TEST_ISSUE}" "${TIMEOUT_PR_CREATION}"); then
    error "PR creation failed"
    return 1
  fi
  TEST_PR="${pr_number}"
  record_timing "pr_creation" "$(elapsed_seconds "${TEST_START_TIME}")"

  # Step 7: Wait for checks
  phase "PHASE 5: CI/CD CHECKS"
  local checks_time
  if ! checks_time=$(wait_for_checks "${TEST_PR}" "${TIMEOUT_CHECKS}"); then
    warn "Checks did not complete successfully within timeout"
  else
    record_timing "checks" "${checks_time}"
  fi

  # Step 8: Wait for merge
  phase "PHASE 6: AUTO-MERGE"
  local merge_time
  if ! merge_time=$(wait_for_merge "${TEST_PR}" "${TIMEOUT_MERGE}"); then
    warn "PR did not auto-merge within timeout"
  else
    record_timing "merge" "${merge_time}"
  fi

  # Step 9: Verify deployment
  phase "PHASE 7: PRODUCTION DEPLOYMENT"
  if verify_deployment "${TEST_PR}" "${TIMEOUT_DEPLOYMENT}"; then
    record_timing "deployment" "$(elapsed_seconds "${TEST_START_TIME}")"
  fi

  # Final summary
  print_summary
}

print_summary() {
  local total_time
  total_time=$(elapsed_seconds "${TEST_START_TIME}")

  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║                    E2E TEST SUMMARY                            ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Issue: #${TEST_ISSUE}${NC}"
  echo -e "${GREEN}║ PR: #${TEST_PR:-N/A}${NC}"
  echo -e "${GREEN}║ Total Time: ${total_time}s ($(printf '%02d:%02d' $((total_time/60)) $((total_time%60))))${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Phase Timings:${NC}"

  for timing in "${PHASE_TIMINGS[@]}"; do
    local phase="${timing%%:*}"
    local duration="${timing##*:}"
    printf "${GREEN}║   %-20s %10s${NC}\n" "${phase}:" "${duration}"
  done

  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  # Links
  echo -e "${BLUE}Issue URL:${NC} https://github.com/${REPO}/issues/${TEST_ISSUE}"
  if [[ -n "${TEST_PR}" ]]; then
    echo -e "${BLUE}PR URL:${NC} https://github.com/${REPO}/pull/${TEST_PR}"
  fi
  echo ""
}

show_usage() {
  cat <<EOF
Usage: $0 [OPTIONS] <test-type>

Test Types:
  simple      - Simple documentation update (5-10 min expected)
  medium      - Bug fix with tests (15-30 min expected)
  complex     - Refactoring multiple files (30-60 min expected)
  custom      - Custom test (requires --title and --body)

Options:
  --title TEXT       Custom issue title (required for 'custom' test)
  --body TEXT        Custom issue body (required for 'custom' test)
  --repo OWNER/NAME  Override repository (default: ${REPO})
  --help             Show this help message

Examples:
  # Run simple test
  $0 simple

  # Run medium test
  $0 medium

  # Run custom test
  $0 custom --title "Add health check endpoint" --body "Implement /health endpoint that returns 200 OK"

EOF
}

main() {
  local test_type=""
  local custom_title=""
  local custom_body=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --title)
        custom_title="$2"
        shift 2
        ;;
      --body)
        custom_body="$2"
        shift 2
        ;;
      --repo)
        REPO="$2"
        shift 2
        ;;
      --help)
        show_usage
        exit 0
        ;;
      simple|medium|complex|custom)
        test_type="$1"
        shift
        ;;
      *)
        error "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  if [[ -z "${test_type}" ]]; then
    error "Test type is required"
    show_usage
    exit 1
  fi

  # Validate gh CLI
  if ! command -v gh &>/dev/null; then
    error "GitHub CLI (gh) is not installed"
    exit 1
  fi

  # Validate jq
  if ! command -v jq &>/dev/null; then
    error "jq is not installed"
    exit 1
  fi

  # Test cases
  case "${test_type}" in
    simple)
      run_e2e_test "simple" \
        "[e2e-test] Add version badge to README" \
        "Add a version badge to the README.md file showing the current version from package.json"
      ;;
    medium)
      run_e2e_test "medium" \
        "[e2e-test] Fix null pointer in user profile" \
        "Fix the null pointer exception that occurs when accessing /api/user/profile without authentication. Add proper error handling and tests."
      ;;
    complex)
      run_e2e_test "complex" \
        "[e2e-test] Refactor authentication middleware" \
        "Refactor the authentication middleware to use async/await instead of callbacks. Update all routes and add comprehensive tests."
      ;;
    custom)
      if [[ -z "${custom_title}" ]] || [[ -z "${custom_body}" ]]; then
        error "Custom test requires --title and --body"
        exit 1
      fi
      run_e2e_test "custom" "${custom_title}" "${custom_body}"
      ;;
  esac
}

main "$@"
