#!/usr/bin/env bash
# Verify main branch protection against baseline (Issue #847)
set -euo pipefail

OWNER="${REPO_OWNER:-$(gh repo view --json owner -q .owner.login)}"
NAME="${REPO_NAME:-$(gh repo view --json name -q .name)}"
FILE="/tmp/protection.json"

echo "=== Branch Protection Verification ==="
echo "Repo: $OWNER/$NAME"

gh api "repos/$OWNER/$NAME/branches/main/protection" > "$FILE" || {
    echo "❌ FAIL: Cannot fetch protection settings"
    exit 1
}

FAILS=0
check() {
    local desc="$1" path="$2" exp="$3"
    act=$(python3 -c "import json,sys; d=json.load(open('$FILE')); print(d$path)" 2>/dev/null || echo "null")
    if [ "$act" = "$exp" ]; then
        echo "✅ PASS: $desc"
    else
        echo "❌ FAIL: $desc (expected $exp, got $act)"
        FAILS=$((FAILS + 1))
    fi
}

check_min() {
    local desc="$1" path="$2" min="$3"
    act=$(python3 -c "import json,sys; d=json.load(open('$FILE')); print(d$path)" 2>/dev/null || echo "0")
    if [ "$act" -ge "$min" ] 2>/dev/null; then
        echo "✅ PASS: $desc"
    else
        echo "❌ FAIL: $desc (expected >= $min, got $act)"
        FAILS=$((FAILS + 1))
    fi
}

check "enforce_admins" "['enforce_admins']['enabled']" "True"
check "allow_force_pushes" "['allow_force_pushes']['enabled']" "False"
check "allow_deletions" "['allow_deletions']['enabled']" "False"
check "conversation_resolution" "['required_conversation_resolution']['enabled']" "True"
check "status_checks_strict" "['required_status_checks']['strict']" "True"
check_min "required_approving_reviews" "['required_pull_request_reviews']['required_approving_review_count']" 1

echo ""
if [ $FAILS -eq 0 ]; then
    echo "✅ All checks passed"
    exit 0
else
    echo "❌ $FAILS check(s) failed - see docs/en/governance/main-branch-protection-baseline.md"
    exit 1
fi
