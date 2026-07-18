#!/bin/bash
# Validation Test Plan for PR #1231 - Orchestrator Label Creation
# Run this script after PR #1231 and #1232 merge

set -e

echo "=== Validation Test for Orchestrator Label Auto-Creation ==="
echo ""

# Step 1: Verify PRs are merged
echo "Step 1: Verifying PRs are merged..."
PR_1231_STATE=$(gh pr view 1231 --repo os-santiago/homedir --json state --jq '.state')
PR_1232_STATE=$(gh pr view 1232 --repo os-santiago/homedir --json state --jq '.state')

if [[ "$PR_1231_STATE" != "MERGED" ]]; then
  echo "❌ ERROR: PR #1231 not merged yet (state: $PR_1231_STATE)"
  exit 1
fi

if [[ "$PR_1232_STATE" != "MERGED" ]]; then
  echo "❌ ERROR: PR #1232 not merged yet (state: $PR_1232_STATE)"
  exit 1
fi

echo "✅ Both PRs merged"
echo ""

# Step 2: Check if test labels exist (they shouldn't)
echo "Step 2: Checking test labels don't exist yet..."
if gh label list --repo os-santiago/homedir --limit 1000 | grep -q "test-orchestrator-validation"; then
  echo "⚠️  WARNING: Label 'test-orchestrator-validation' already exists"
else
  echo "✅ Label 'test-orchestrator-validation' does not exist (good)"
fi

if gh label list --repo os-santiago/homedir --limit 1000 | grep -q "test-pipeline-continuation"; then
  echo "⚠️  WARNING: Label 'test-pipeline-continuation' already exists"
else
  echo "✅ Label 'test-pipeline-continuation' does not exist (good)"
fi
echo ""

# Step 3: Create first test issue
echo "Step 3: Creating first test issue..."
ISSUE_BODY=$(cat <<'EOF'
## Problem Statement
This is a test issue to validate that the pipeline orchestrator
correctly handles non-existent labels by auto-creating them.

## Expected Behavior
Issue should be created with auto-created labels

## Acceptance Criteria
- [ ] Issue created successfully
- [ ] Labels auto-created by orchestrator

## Complexity
- [x] Simple (test only)

---

**Test Type**: Orchestrator label validation
**Related**: PR #1231, Issue #1141
EOF
)

ISSUE_URL=$(gh issue create --repo os-santiago/homedir \
  --title "[test] Orchestrator label validation - step 1" \
  --label "documentation,ready-to-implement" \
  --body "$ISSUE_BODY")

ISSUE_NUMBER=$(echo "$ISSUE_URL" | grep -oE '[0-9]+$')

echo "✅ Issue created: $ISSUE_URL (Issue #$ISSUE_NUMBER)"
echo ""

# Step 4: Wait for worker to process
echo "Step 4: Waiting for worker to process issue #$ISSUE_NUMBER..."
echo "   This may take 3-20 minutes depending on:"
echo "   - Admission review"
echo "   - Queue position"
echo "   - SCC execution"
echo "   - CI checks"
echo "   - Auto-merge"
echo ""
echo "   You can monitor progress at:"
echo "   https://github.com/os-santiago/homedir/issues/$ISSUE_NUMBER"
echo ""

# Step 5: Instructions for validation
echo "Step 5: Validation checklist (manual):"
echo ""
echo "After issue #$ISSUE_NUMBER closes, verify:"
echo "  [ ] PR was created and merged"
echo "  [ ] Issue #$ISSUE_NUMBER is closed"
echo "  [ ] Issue #$(($ISSUE_NUMBER + 1)) was auto-created by orchestrator"
echo "  [ ] Label 'test-orchestrator-validation' exists"
echo "  [ ] Label 'test-pipeline-continuation' exists"
echo "  [ ] Issue #$(($ISSUE_NUMBER + 1)) has label 'test-pipeline-continuation'"
echo "  [ ] Issue #$(($ISSUE_NUMBER + 1)) has labels 'scc-accepted' and 'scc-queued'"
echo ""
echo "To check orchestrator logs on VPS:"
echo "  ssh homedir-sdlc@vps"
echo "  tail -f ~/.local/state/homedir-sdlc/logs/worker.log | grep orchestrator"
echo ""

# Step 6: Cleanup instructions
echo "Step 6: Cleanup after validation (manual):"
echo ""
echo "  # Close test issues"
echo "  gh issue close $ISSUE_NUMBER --repo os-santiago/homedir -c 'Test completed'"
echo "  gh issue close $(($ISSUE_NUMBER + 1)) --repo os-santiago/homedir -c 'Test completed'"
echo ""
echo "  # Delete test labels"
echo "  gh label delete test-orchestrator-validation --repo os-santiago/homedir --yes"
echo "  gh label delete test-pipeline-continuation --repo os-santiago/homedir --yes"
echo ""

echo "=== Validation test initiated ==="
echo "Created issue #$ISSUE_NUMBER - Monitor and validate manually"
