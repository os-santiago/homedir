#!/bin/bash
# SDLC E2E Test Runner - Uses existing real issues
# Tests the complete AI SDLC flow using pre-existing GitHub issues

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

show_issue_info() {
  local issue="$1"

  log "Fetching issue #${issue} information..."

  local issue_json
  issue_json=$(gh issue view "${issue}" -R "${REPO}" --json number,title,state,labels,body)

  local title
  title=$(echo "${issue_json}" | jq -r '.title')

  local state
  state=$(echo "${issue_json}" | jq -r '.state')

  local labels
  labels=$(echo "${issue_json}" | jq -r '.labels[].name' | tr '\n' ',' | sed 's/,$//')

  echo ""
  echo -e "${BLUE}Issue Information:${NC}"
  echo -e "  Number: #${issue}"
  echo -e "  Title: ${title}"
  echo -e "  State: ${state}"
  echo -e "  Labels: ${labels}"
  echo ""
}

validate_issue_eligibility() {
  local issue="$1"

  log "Validating issue #${issue} eligibility for E2E test..."

  local state
  state=$(gh issue view "${issue}" -R "${REPO}" --json state --jq '.state')

  if [[ "${state}" != "OPEN" ]]; then
    error "Issue #${issue} is not open (state: ${state})"
    return 1
  fi

  local labels
  labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  # Check if already in SDLC flow
  if echo "${labels}" | grep -qE "scc-running|scc-failed|scc-merged"; then
    warn "Issue #${issue} is already in or has completed the SDLC flow"
    echo ""
    echo "Current labels: ${labels}"
    echo ""
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      error "Test aborted by user"
      return 1
    fi
  fi

  success "Issue #${issue} is eligible for E2E test"
  return 0
}

trigger_sdlc_flow() {
  local issue="$1"

  log "Triggering SDLC flow for issue #${issue}..."

  # Add ready-to-implement label
  gh issue edit "${issue}" -R "${REPO}" --add-label "ready-to-implement"

  success "Added 'ready-to-implement' label to issue #${issue}"
  log "Admission gateway should process this within ~5 seconds"
}

monitor_issue_flow() {
  local issue="$1"
  local timeout="${2:-3600}"  # 1 hour default

  log "Monitoring issue #${issue} through SDLC flow..."
  log "Timeout: ${timeout}s"
  echo ""

  local start
  start=$(date +%s)

  local last_labels=""
  local phase_start=${start}

  while true; do
    local current_labels
    current_labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',' | sed 's/,$//')

    # Detect label change
    if [[ "${current_labels}" != "${last_labels}" ]]; then
      local now
      now=$(date +%s)
      local phase_duration=$((now - phase_start))

      if [[ -n "${last_labels}" ]]; then
        log "Phase completed in ${phase_duration}s"
      fi

      log "Labels changed: ${current_labels}"
      last_labels="${current_labels}"
      phase_start=${now}

      # Check for terminal states
      if echo "${current_labels}" | grep -q "scc-merged"; then
        success "Issue completed successfully - merged to production!"
        return 0
      fi

      if echo "${current_labels}" | grep -q "scc-failed"; then
        error "Issue failed during SDLC flow"
        log "Fetching failure comments..."
        gh issue view "${issue}" -R "${REPO}" --comments | tail -10
        return 1
      fi

      if echo "${current_labels}" | grep -q "scc-rejected"; then
        error "Issue rejected during admission"
        log "Fetching rejection reason..."
        gh issue view "${issue}" -R "${REPO}" --comments | tail -5
        return 1
      fi

      if echo "${current_labels}" | grep -q "needs-human"; then
        warn "Issue requires human intervention"
        log "Fetching details..."
        gh issue view "${issue}" -R "${REPO}" --comments | tail -10
        return 2
      fi
    fi

    # Check for PR
    local pr_json
    pr_json=$(gh pr list -R "${REPO}" --search "in:title closes #${issue}" --json number,state --limit 1)

    if [[ "${pr_json}" != "[]" ]]; then
      local pr_number
      pr_number=$(echo "${pr_json}" | jq -r '.[0].number // empty')

      if [[ -n "${pr_number}" ]]; then
        log "PR #${pr_number} found"

        local pr_state
        pr_state=$(echo "${pr_json}" | jq -r '.[0].state')

        if [[ "${pr_state}" == "MERGED" ]]; then
          success "PR #${pr_number} merged!"
          # Wait for issue to close
          sleep 5
          continue
        fi
      fi
    fi

    # Check timeout
    local elapsed
    elapsed=$(( $(date +%s) - start ))

    if [[ ${elapsed} -ge ${timeout} ]]; then
      error "Timeout monitoring issue (${timeout}s elapsed)"
      log "Current labels: ${current_labels}"
      return 1
    fi

    # Progress indicator
    local minutes=$((elapsed / 60))
    local seconds=$((elapsed % 60))
    printf "\r  Elapsed: %02d:%02d | Current phase: %-40s" ${minutes} ${seconds} "${current_labels:0:40}"

    sleep 10
  done
}

run_e2e_test() {
  local issue="$1"

  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║              SDLC E2E TEST - REAL ISSUE                        ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Repository: ${REPO}${NC}"
  echo -e "${GREEN}║ Issue: #${issue}${NC}"
  echo -e "${GREEN}║ Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  # Step 1: Show issue info
  show_issue_info "${issue}"

  # Step 2: Validate eligibility
  if ! validate_issue_eligibility "${issue}"; then
    return 1
  fi

  # Step 3: Trigger SDLC flow
  trigger_sdlc_flow "${issue}"

  echo ""
  log "Waiting 10 seconds for admission gateway to process..."
  sleep 10
  echo ""

  # Step 4: Monitor flow
  local result=0
  monitor_issue_flow "${issue}" 3600 || result=$?

  echo ""
  echo ""

  # Final summary
  echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║                    E2E TEST SUMMARY                            ║${NC}"
  echo -e "${BLUE}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${BLUE}║ Issue: #${issue}${NC}"

  if [[ ${result} -eq 0 ]]; then
    echo -e "${BLUE}║ Result: ${GREEN}SUCCESS${NC}${BLUE} - Issue completed and merged${NC}"
  elif [[ ${result} -eq 1 ]]; then
    echo -e "${BLUE}║ Result: ${RED}FAILED${NC}${BLUE} - Issue failed or timed out${NC}"
  elif [[ ${result} -eq 2 ]]; then
    echo -e "${BLUE}║ Result: ${YELLOW}NEEDS HUMAN${NC}${BLUE} - Manual intervention required${NC}"
  fi

  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "${BLUE}View issue:${NC} https://github.com/${REPO}/issues/${issue}"
  echo ""

  return ${result}
}

list_eligible_issues() {
  log "Finding eligible issues for E2E testing..."

  # Find open issues without SDLC labels
  local issues_json
  issues_json=$(gh issue list -R "${REPO}" \
    --state open \
    --json number,title,labels \
    --limit 50)

  echo ""
  echo "Eligible issues (open, not in SDLC flow):"
  echo ""

  echo "${issues_json}" | jq -r '.[] | "\(.number)|\(.title)|\(.labels[].name)"' | while IFS='|' read -r number title labels; do
    # Skip if already in SDLC flow
    if echo "${labels}" | grep -qE "scc-running|scc-queued|scc-failed|scc-merged"; then
      continue
    fi

    printf "  #%-6s %s\n" "${number}" "${title:0:70}"
  done

  echo ""
}

show_usage() {
  cat <<EOF
Usage: $0 [OPTIONS] <issue_number>

Run E2E test on an existing real GitHub issue.

Arguments:
  issue_number        GitHub issue number to test (e.g., 1234)

Options:
  --list              List eligible issues for testing
  --timeout SECONDS   Override default timeout (default: 3600)
  --help              Show this help message

Examples:
  # List eligible issues
  $0 --list

  # Run E2E test on issue #1234
  $0 1234

  # Run with custom timeout (30 minutes)
  $0 --timeout 1800 1234

  # Monitor in real-time (separate terminal)
  ${SCRIPT_DIR}/monitor-dashboard.sh watch 1234

EOF
}

main() {
  local issue=""
  local timeout=3600
  local list_mode=false

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --list)
        list_mode=true
        shift
        ;;
      --timeout)
        timeout="$2"
        shift 2
        ;;
      --help)
        show_usage
        exit 0
        ;;
      [0-9]*)
        issue="$1"
        shift
        ;;
      *)
        error "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  # Validate dependencies
  if ! command -v gh &>/dev/null; then
    error "GitHub CLI (gh) is not installed"
    exit 1
  fi

  if ! command -v jq &>/dev/null; then
    error "jq is not installed"
    exit 1
  fi

  # Check gh auth
  if ! gh auth status &>/dev/null; then
    error "GitHub CLI is not authenticated"
    log "Run: gh auth login"
    exit 1
  fi

  if [[ "${list_mode}" == "true" ]]; then
    list_eligible_issues
    exit 0
  fi

  if [[ -z "${issue}" ]]; then
    error "Issue number is required"
    show_usage
    exit 1
  fi

  run_e2e_test "${issue}"
}

main "$@"
