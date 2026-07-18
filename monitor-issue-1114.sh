#!/bin/bash
# Monitor Issue #1114 Complete Lifecycle
# Tracks: scc-queued → scc-running → scc-pr-open → CI → auto-merge → closed

set -euo pipefail

ISSUE=1114
REPO="os-santiago/homedir"
CHECK_INTERVAL=30  # seconds

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log() {
  echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"
}

log_success() {
  echo -e "${GREEN}[$(date '+%H:%M:%S')] ✅ $*${NC}"
}

log_progress() {
  echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⏳ $*${NC}"
}

log_info() {
  echo -e "${BLUE}[$(date '+%H:%M:%S')] ℹ️  $*${NC}"
}

get_issue_state() {
  gh issue view "${ISSUE}" --repo "${REPO}" --json labels,state,updatedAt \
    --jq '{labels: [.labels[].name], state: .state, updatedAt: .updatedAt}'
}

get_pr_for_issue() {
  gh pr list --repo "${REPO}" --search "head:scc/issue-${ISSUE}" --state all \
    --json number,state,url,mergeable,mergeStateStatus,checks
}

check_pr_checks() {
  local pr_number="$1"
  gh pr view "${pr_number}" --repo "${REPO}" --json statusCheckRollup \
    --jq '{
      total: (.statusCheckRollup | length),
      pending: [.statusCheckRollup[] | select(.state == "PENDING")] | length,
      success: [.statusCheckRollup[] | select(.state == "SUCCESS")] | length,
      failure: [.statusCheckRollup[] | select(.state == "FAILURE")] | length
    }'
}

print_separator() {
  echo ""
  echo "═══════════════════════════════════════════════════════════════════"
  echo ""
}

monitor_cycle() {
  local cycle=0
  local last_state=""
  local pr_created=false
  local pr_number=""

  log "🔍 Starting monitor for Issue #${ISSUE}"
  log "Repository: ${REPO}"
  log "Check interval: ${CHECK_INTERVAL}s"
  print_separator

  while true; do
    cycle=$((cycle + 1))
    log "Cycle #${cycle}"

    # Get issue state
    issue_data=$(get_issue_state)
    labels=$(echo "$issue_data" | jq -r '.labels | join(", ")')
    state=$(echo "$issue_data" | jq -r '.state')
    updated=$(echo "$issue_data" | jq -r '.updatedAt')

    # Determine current phase
    current_phase="unknown"
    if echo "$labels" | grep -q "scc-queued"; then
      current_phase="queued"
    elif echo "$labels" | grep -q "scc-running"; then
      current_phase="running"
    elif echo "$labels" | grep -q "scc-pr-open"; then
      current_phase="pr-open"
    elif echo "$labels" | grep -q "scc-waiting-checks"; then
      current_phase="waiting-checks"
    elif echo "$labels" | grep -q "scc-approved"; then
      current_phase="approved"
    elif echo "$labels" | grep -q "scc-merged"; then
      current_phase="merged"
    elif echo "$labels" | grep -q "scc-failed"; then
      current_phase="failed"
    elif echo "$labels" | grep -q "needs-human"; then
      current_phase="needs-human"
    fi

    # Detect state change
    if [[ "$current_phase" != "$last_state" ]]; then
      print_separator
      log_success "STATE CHANGE: ${last_state:-init} → ${current_phase}"
      print_separator
      last_state="$current_phase"
    fi

    # Display current state
    log_info "Issue State: ${state}"
    log_info "Phase: ${current_phase}"
    log_info "Labels: ${labels}"
    log_info "Updated: ${updated}"

    # Check for PR
    pr_data=$(get_pr_for_issue)
    if [[ "$pr_data" != "[]" && "$pr_data" != "" ]]; then
      if [[ "$pr_created" == false ]]; then
        print_separator
        log_success "PR CREATED!"
        print_separator
        pr_created=true
      fi

      pr_number=$(echo "$pr_data" | jq -r '.[0].number')
      pr_state=$(echo "$pr_data" | jq -r '.[0].state')
      pr_url=$(echo "$pr_data" | jq -r '.[0].url')
      merge_state=$(echo "$pr_data" | jq -r '.[0].mergeStateStatus')

      log_info "PR #${pr_number}: ${pr_url}"
      log_info "PR State: ${pr_state}"
      log_info "Merge State: ${merge_state}"

      # Check CI status
      if [[ "$pr_state" == "OPEN" ]]; then
        checks=$(check_pr_checks "${pr_number}")
        total=$(echo "$checks" | jq -r '.total')
        pending=$(echo "$checks" | jq -r '.pending')
        success=$(echo "$checks" | jq -r '.success')
        failure=$(echo "$checks" | jq -r '.failure')

        if [[ "$total" -gt 0 ]]; then
          log_info "CI Checks: ${success}/${total} passed, ${pending} pending, ${failure} failed"

          if [[ "$success" -eq "$total" ]]; then
            log_success "All CI checks passed! ✨"
          fi
        fi
      fi

      # Check if merged
      if [[ "$pr_state" == "MERGED" ]]; then
        print_separator
        log_success "PR MERGED! 🎉"
        print_separator
      fi
    else
      log_info "PR: Not created yet"
    fi

    # Check if issue closed
    if [[ "$state" == "CLOSED" ]]; then
      print_separator
      log_success "ISSUE CLOSED! 🎉"
      log_success "Complete lifecycle finished successfully!"
      print_separator

      # Final summary
      echo ""
      echo "📊 FINAL SUMMARY"
      echo "════════════════"
      echo "Issue #${ISSUE}: CLOSED"
      if [[ -n "$pr_number" ]]; then
        echo "PR #${pr_number}: MERGED"
        echo "URL: ${pr_url}"
      fi
      echo ""
      log_success "Monitor completed successfully! ✅"
      break
    fi

    # Check for terminal failure states
    if echo "$labels" | grep -q "scc-failed"; then
      print_separator
      log "❌ FAILED: Issue marked as scc-failed"
      print_separator
      log "Check issue comments for failure reason:"
      log "  gh issue view ${ISSUE} --repo ${REPO} --comments"
      break
    fi

    if echo "$labels" | grep -q "needs-human"; then
      print_separator
      log "⚠️  NEEDS HUMAN: Manual intervention required"
      print_separator
      log "Check issue comments for details:"
      log "  gh issue view ${ISSUE} --repo ${REPO} --comments"
      break
    fi

    # Wait for next check
    echo ""
    log_progress "Next check in ${CHECK_INTERVAL}s... (Ctrl+C to stop)"
    sleep "${CHECK_INTERVAL}"
    echo ""
  done
}

# Main
main() {
  echo ""
  echo "╔═══════════════════════════════════════════════════════════════╗"
  echo "║         Issue #${ISSUE} Lifecycle Monitor                          ║"
  echo "║         FIFO Queue - First Autonomous Processing              ║"
  echo "╚═══════════════════════════════════════════════════════════════╝"
  echo ""

  monitor_cycle
}

main "$@"
