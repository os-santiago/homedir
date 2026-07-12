#!/bin/bash
# Sequential Issue Monitor for AI SDLC
# Monitors one issue at a time, checking every 30 minutes

set -euo pipefail

REPO="${1:-os-santiago/homedir}"
ISSUE="${2:-}"
INTERVAL_MINUTES="${3:-30}"

if [[ -z "$ISSUE" ]]; then
  echo "Usage: $0 [repo] <issue_number> [interval_minutes]"
  echo "Example: $0 os-santiago/homedir 1104 30"
  exit 1
fi

INTERVAL_SECONDS=$((INTERVAL_MINUTES * 60))

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        Sequential Issue Monitor - AI SDLC                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Repository: $REPO"
echo "Issue:      #$ISSUE"
echo "Interval:   $INTERVAL_MINUTES minutes"
echo "Started:    $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

check_count=0

while true; do
  check_count=$((check_count + 1))
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Check #$check_count at $(date '+%H:%M:%S')"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Get issue data
  ISSUE_DATA=$(gh issue view "$ISSUE" -R "$REPO" --json state,labels,closedAt 2>/dev/null || echo "")

  if [[ -z "$ISSUE_DATA" ]]; then
    echo "❌ ERROR: Could not fetch issue #$ISSUE"
    exit 1
  fi

  STATE=$(echo "$ISSUE_DATA" | jq -r '.state')
  LABELS=$(echo "$ISSUE_DATA" | jq -r '.labels[].name' | sort | tr '\n' ',' | sed 's/,$//')

  echo "State:  $STATE"
  echo "Labels: $LABELS"

  # Check for PR
  PR_DATA=$(gh pr list -R "$REPO" --search "$ISSUE in:body OR $ISSUE in:title" --json number,state,url --jq '.[0] // empty' 2>/dev/null || echo "")

  if [[ -n "$PR_DATA" ]]; then
    PR_NUM=$(echo "$PR_DATA" | jq -r '.number')
    PR_STATE=$(echo "$PR_DATA" | jq -r '.state')
    PR_URL=$(echo "$PR_DATA" | jq -r '.url')
    echo "PR:     #$PR_NUM ($PR_STATE)"
    echo "URL:    $PR_URL"

    # Check if PR merged
    if [[ "$PR_STATE" == "MERGED" ]]; then
      echo ""
      echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
      echo "┃  ✅ SUCCESS! Issue #$ISSUE completed via PR #$PR_NUM      ┃"
      echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
      echo ""
      echo "Total checks: $check_count"
      echo "Total time:   $((check_count * INTERVAL_MINUTES)) minutes"
      exit 0
    fi
  else
    echo "PR:     none"
  fi

  # Check for terminal failure states
  if echo "$LABELS" | grep -q "scc-failed"; then
    echo ""
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┃  ❌ FAILED! Issue #$ISSUE marked as scc-failed          ┃"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
    echo ""
    echo "Recent comments:"
    gh issue view "$ISSUE" -R "$REPO" --comments 2>/dev/null | tail -20
    exit 1
  fi

  if echo "$LABELS" | grep -q "needs-human"; then
    echo ""
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┃  ⚠️  BLOCKED! Issue #$ISSUE needs human intervention    ┃"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
    exit 1
  fi

  # Status indicator
  if echo "$LABELS" | grep -q "scc-running"; then
    echo "Status: ▶️  PROCESSING - SCC is working"
  elif echo "$LABELS" | grep -q "scc-queued"; then
    echo "Status: ⏳ QUEUED - Waiting for worker"
  elif echo "$LABELS" | grep -q "scc-waiting-checks"; then
    echo "Status: ⏸️  CI CHECKS - Waiting for GitHub Actions"
  elif echo "$LABELS" | grep -q "scc-approved"; then
    echo "Status: ✓ APPROVED - Ready for auto-merge"
  elif echo "$LABELS" | grep -q "scc-under-review"; then
    echo "Status: 👀 REVIEW - Processing feedback"
  else
    echo "Status: 🔵 UNKNOWN - Labels: $LABELS"
  fi

  echo ""
  NEXT_CHECK=$(date -d "+$INTERVAL_MINUTES minutes" '+%H:%M:%S' 2>/dev/null || date -v+${INTERVAL_MINUTES}M '+%H:%M:%S' 2>/dev/null || echo "in $INTERVAL_MINUTES min")
  echo "Next check: $NEXT_CHECK"
  echo ""

  sleep "$INTERVAL_SECONDS"
done
