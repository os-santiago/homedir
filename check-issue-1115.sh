#!/bin/bash
# Quick status check for Issue #1115

ISSUE=1115
REPO="os-santiago/homedir"

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         Issue #1115 Status Check                              ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Issue state
echo "📋 Issue State:"
gh issue view ${ISSUE} --repo ${REPO} --json number,title,state,labels,updatedAt \
  --jq '"  #\(.number): \(.title)
  State: \(.state)
  Updated: \(.updatedAt)
  Labels: \(.labels | map(.name) | join(", "))"'

echo ""

# Check for PR
echo "🔀 Pull Request:"
PR_DATA=$(gh pr list --repo ${REPO} --search "head:scc/issue-${ISSUE}" --state all --json number,state,url)

if [[ "$PR_DATA" == "[]" ]]; then
  echo "  Status: Not created yet"
else
  echo "$PR_DATA" | python3 -c "
import json, sys
prs = json.load(sys.stdin)
for pr in prs:
    print(f'  PR #{pr[\"number\"]}: {pr[\"state\"]}')
    print(f'  URL: {pr[\"url\"]}')
"
fi

echo ""

# Determine phase
LABELS=$(gh issue view ${ISSUE} --repo ${REPO} --json labels --jq '[.labels[].name] | join(",")')

if echo "$LABELS" | grep -q "scc-queued"; then
  PHASE="⏳ Queued (waiting for worker)"
elif echo "$LABELS" | grep -q "scc-running"; then
  PHASE="🏃 Running (SCC agent working)"
elif echo "$LABELS" | grep -q "scc-pr-open"; then
  PHASE="🔀 PR Open (waiting for CI)"
elif echo "$LABELS" | grep -q "scc-waiting-checks"; then
  PHASE="⏱️  Waiting for CI checks"
elif echo "$LABELS" | grep -q "scc-approved"; then
  PHASE="✅ Approved (ready to merge)"
elif echo "$LABELS" | grep -q "scc-merged"; then
  PHASE="🎉 Merged and closed"
elif echo "$LABELS" | grep -q "scc-failed"; then
  PHASE="❌ Failed"
elif echo "$LABELS" | grep -q "needs-human"; then
  PHASE="⚠️  Needs human intervention"
else
  PHASE="❓ Unknown"
fi

echo "🔍 Current Phase: $PHASE"
echo ""
echo "═══════════════════════════════════════════════════════════════"
