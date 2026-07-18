#!/bin/bash
# Test Orchestrator Flow - Complex issue decomposition
# Tests the orchestrator mode where complex issues are broken into sub-issues

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
TEST_LABEL="e2e-test-orchestrator"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
  echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $*"
}

success() {
  echo -e "${GREEN}✓${NC} $*"
}

error() {
  echo -e "${RED}✗${NC} $*"
}

warn() {
  echo -e "${YELLOW}⚠${NC} $*"
}

create_epic_issue() {
  local title="$1"
  local description="$2"

  log "Creating epic issue for orchestrator flow..."

  local body
  body=$(cat <<EOF
${description}

## Orchestrator Mode

This issue is intentionally complex and should trigger the orchestrator mode, which will:
1. Analyze the requirements
2. Decompose into atomic sub-issues
3. Create sub-issues automatically
4. Track progress as each sub-issue is completed
5. Close this epic when all sub-issues are merged

## Expected Sub-tasks

The AI should identify logical decomposition points and create atomic issues for each.

---

**E2E Test Metadata**
- Test Type: orchestrator
- Test Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)
- Script: test-orchestrator-flow.sh
EOF
)

  local issue_number
  issue_number=$(gh issue create \
    -R "${REPO}" \
    --title "${title}" \
    --body "${body}" \
    --label "${TEST_LABEL},epic" \
    --assignee "@me" \
    --json number \
    --jq '.number')

  if [[ -z "${issue_number}" ]]; then
    error "Failed to create epic issue"
    return 1
  fi

  success "Created epic issue #${issue_number}"
  echo "${issue_number}"
}

trigger_orchestrator() {
  local issue="$1"

  log "Triggering orchestrator by adding 'ready-to-implement' label..."
  gh issue edit "${issue}" -R "${REPO}" --add-label "ready-to-implement"

  success "Orchestrator triggered for issue #${issue}"
}

wait_for_decomposition() {
  local epic_issue="$1"
  local timeout="${2:-600}"  # 10 minutes default
  local start
  start=$(date +%s)

  log "Waiting for orchestrator to decompose epic #${epic_issue} into sub-issues..."

  while true; do
    # Check for decomposition label
    local labels
    labels=$(gh issue view "${epic_issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

    if echo "${labels}" | grep -q "scc-needs-decomposition"; then
      log "Issue marked for decomposition"
    fi

    # Look for sub-issues
    local sub_issues
    sub_issues=$(gh issue list -R "${REPO}" \
      --search "parent:#${epic_issue} in:body" \
      --json number,title,labels \
      --limit 50)

    local sub_count
    sub_count=$(echo "${sub_issues}" | jq 'length')

    if [[ ${sub_count} -gt 0 ]]; then
      local elapsed
      elapsed=$(( $(date +%s) - start ))
      success "Found ${sub_count} sub-issue(s) after ${elapsed}s"

      echo "${sub_issues}" | jq -r '.[] | "#\(.number): \(.title)"' | while read -r line; do
        echo "  ${line}"
      done

      echo "${sub_issues}"
      return 0
    fi

    # Check for failure
    if echo "${labels}" | grep -qE "scc-failed|scc-rejected|needs-human"; then
      error "Epic issue entered failure state: ${labels}"
      return 1
    fi

    local elapsed
    elapsed=$(( $(date +%s) - start ))
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout waiting for decomposition (${timeout}s elapsed)"
      return 1
    fi

    log "Waiting for decomposition... (${elapsed}s elapsed, ${sub_count} sub-issues found)"
    sleep 15
  done
}

monitor_sub_issue_progress() {
  local epic_issue="$1"
  local sub_issues_json="$2"
  local timeout="${3:-3600}"  # 1 hour default

  local total_sub_issues
  total_sub_issues=$(echo "${sub_issues_json}" | jq 'length')

  log "Monitoring ${total_sub_issues} sub-issue(s) for epic #${epic_issue}..."

  local start
  start=$(date +%s)

  while true; do
    # Get current status of all sub-issues
    local completed=0
    local in_progress=0
    local failed=0

    echo "${sub_issues_json}" | jq -r '.[].number' | while read -r sub_number; do
      local state
      state=$(gh issue view "${sub_number}" -R "${REPO}" --json state --jq '.state')

      local labels
      labels=$(gh issue view "${sub_number}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

      if [[ "${state}" == "CLOSED" ]] || echo "${labels}" | grep -q "scc-merged"; then
        completed=$((completed + 1))
      elif echo "${labels}" | grep -qE "scc-failed|scc-rejected"; then
        failed=$((failed + 1))
      elif echo "${labels}" | grep -qE "scc-running|scc-queued"; then
        in_progress=$((in_progress + 1))
      fi
    done

    log "Progress: ${completed}/${total_sub_issues} completed, ${in_progress} in progress, ${failed} failed"

    # Check if all completed
    if [[ ${completed} -eq ${total_sub_issues} ]]; then
      success "All sub-issues completed!"
      return 0
    fi

    # Check if any failed
    if [[ ${failed} -gt 0 ]]; then
      warn "${failed} sub-issue(s) failed - continuing to monitor others"
    fi

    local elapsed
    elapsed=$(( $(date +%s) - start ))
    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout monitoring sub-issues (${timeout}s elapsed)"
      return 1
    fi

    sleep 30
  done
}

verify_epic_completion() {
  local epic_issue="$1"

  log "Verifying epic issue #${epic_issue} completion..."

  local state
  state=$(gh issue view "${epic_issue}" -R "${REPO}" --json state --jq '.state')

  local labels
  labels=$(gh issue view "${epic_issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  if [[ "${state}" == "CLOSED" ]]; then
    success "Epic issue is closed"
    return 0
  elif echo "${labels}" | grep -q "scc-merged"; then
    success "Epic issue marked as merged"
    return 0
  else
    warn "Epic issue not yet closed (state: ${state}, labels: ${labels})"
    return 1
  fi
}

run_orchestrator_test() {
  local title="$1"
  local description="$2"

  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║           ORCHESTRATOR FLOW E2E TEST                           ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Test: ${title}${NC}"
  echo -e "${GREEN}║ Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  # Step 1: Create epic issue
  local epic_issue
  if ! epic_issue=$(create_epic_issue "${title}" "${description}"); then
    error "Failed to create epic issue"
    return 1
  fi

  # Step 2: Trigger orchestrator
  trigger_orchestrator "${epic_issue}"

  # Step 3: Wait for decomposition
  local sub_issues_json
  if ! sub_issues_json=$(wait_for_decomposition "${epic_issue}" 600); then
    error "Decomposition failed"
    return 1
  fi

  # Step 4: Monitor sub-issue progress
  if ! monitor_sub_issue_progress "${epic_issue}" "${sub_issues_json}" 3600; then
    warn "Sub-issue monitoring timed out or failed"
  fi

  # Step 5: Verify epic completion
  verify_epic_completion "${epic_issue}"

  # Summary
  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║                  ORCHESTRATOR TEST SUMMARY                     ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Epic Issue: #${epic_issue}${NC}"
  echo -e "${GREEN}║ Sub-issues: $(echo "${sub_issues_json}" | jq 'length')${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  echo -e "${BLUE}Epic URL:${NC} https://github.com/${REPO}/issues/${epic_issue}"
  echo ""

  echo "${sub_issues_json}" | jq -r '.[] | "#\(.number): \(.title)"' | while read -r line; do
    echo "  ${line}"
  done
  echo ""
}

show_usage() {
  cat <<EOF
Usage: $0 [OPTIONS] <test-case>

Test Cases:
  auth-system     - Implement complete authentication system
  api-crud        - Build CRUD API for a resource
  custom          - Custom epic (requires --title and --description)

Options:
  --title TEXT          Custom epic title
  --description TEXT    Custom epic description
  --help                Show this help

Examples:
  # Test authentication system decomposition
  $0 auth-system

  # Test API CRUD decomposition
  $0 api-crud

  # Custom epic
  $0 custom \
    --title "Implement notification system" \
    --description "Build a complete notification system with email, SMS, and push notifications"

EOF
}

main() {
  local test_case=""
  local custom_title=""
  local custom_description=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --title)
        custom_title="$2"
        shift 2
        ;;
      --description)
        custom_description="$2"
        shift 2
        ;;
      --help)
        show_usage
        exit 0
        ;;
      auth-system|api-crud|custom)
        test_case="$1"
        shift
        ;;
      *)
        error "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  if [[ -z "${test_case}" ]]; then
    error "Test case is required"
    show_usage
    exit 1
  fi

  # Validate dependencies
  if ! command -v gh &>/dev/null; then
    error "GitHub CLI (gh) is not installed"
    exit 1
  fi

  if ! command -v jq &>/dev/null; then
    error "jq is not installed"
    exit 1
  fi

  case "${test_case}" in
    auth-system)
      run_orchestrator_test \
        "[e2e-orchestrator] Implement user authentication system" \
        "Create a complete authentication system including:
- User login with email/password
- User logout and session management
- Password reset flow with email verification
- JWT token generation and validation
- Protected route middleware
- Comprehensive authentication tests

This should be broken down into logical, atomic sub-tasks that can be implemented independently."
      ;;
    api-crud)
      run_orchestrator_test \
        "[e2e-orchestrator] Build CRUD API for Projects resource" \
        "Implement a complete REST API for managing Projects:
- GET /api/projects - List all projects
- GET /api/projects/:id - Get single project
- POST /api/projects - Create new project
- PUT /api/projects/:id - Update project
- DELETE /api/projects/:id - Delete project
- Input validation and error handling
- Database migrations for projects table
- Integration tests for all endpoints

Break this down into independent, testable sub-tasks."
      ;;
    custom)
      if [[ -z "${custom_title}" ]] || [[ -z "${custom_description}" ]]; then
        error "Custom test requires --title and --description"
        exit 1
      fi
      run_orchestrator_test "${custom_title}" "${custom_description}"
      ;;
  esac
}

main "$@"
