#!/bin/bash
# Monitor E2E test issue progress
# Usage: ./monitor-issue-e2e.sh <issue_number>

set -euo pipefail

ISSUE="${1:-1099}"
REPO="os-santiago/homedir"
INTERVAL=30  # seconds

echo "Monitoring issue #${ISSUE} in ${REPO}"
echo "Checking every ${INTERVAL} seconds..."
echo "Press Ctrl+C to stop"
echo ""

while true; do
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

  # Get issue labels
  LABELS=$(gh issue view "${ISSUE}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',' | sed 's/,$//')

  # Get PR if exists
  PR_INFO=$(gh pr list -R "${REPO}" --search "closes #${ISSUE}" --json number,state --jq '.[0] // empty' 2>/dev/null || echo "")

  echo "[${TIMESTAMP}] Labels: ${LABELS}"

  if [[ -n "${PR_INFO}" ]]; then
    PR_NUM=$(echo "${PR_INFO}" | jq -r '.number')
    PR_STATE=$(echo "${PR_INFO}" | jq -r '.state')
    echo "[${TIMESTAMP}] PR #${PR_NUM} (${PR_STATE}) found"
  fi

  # Check for terminal states
  if echo "${LABELS}" | grep -q "scc-failed"; then
    echo ""
    echo "❌ Worker FAILED - checking comments..."
    gh issue view "${ISSUE}" -R "${REPO}" --comments | tail -20
    exit 1
  fi

  if [[ -n "${PR_INFO}" ]] && [[ "${PR_STATE}" == "MERGED" ]]; then
    echo ""
    echo "✅ PR MERGED - E2E test PASSED!"
    exit 0
  fi

  sleep "${INTERVAL}"
done
