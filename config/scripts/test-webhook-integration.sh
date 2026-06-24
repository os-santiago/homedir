#!/bin/bash
# Test script for GitHub webhook → WOS → Discord integration (Issue #864)

set -euo pipefail

WEBHOOK_URL="https://homedir.opensourcesantiago.io/github-webhook"
REPO="os-santiago/homedir"
HOOK_ID="644594980"

# Track test results
declare -A RESULTS
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== GitHub Webhook Integration Test ==="
echo ""

# Test 1: Webhook endpoint
echo "1. Testing webhook endpoint..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$WEBHOOK_URL" || echo "000")
if [ "$HTTP_CODE" = "501" ] || [ "$HTTP_CODE" = "405" ] || [ "$HTTP_CODE" = "403" ]; then
    echo "   ✅ Endpoint active (HTTP $HTTP_CODE)"
    RESULTS[endpoint]="PASS"
else
    echo "   ❌ Unexpected response (HTTP $HTTP_CODE)"
    RESULTS[endpoint]="FAIL"
fi
echo ""

# Test 2: Webhook configuration
echo "2. Checking webhook configuration..."
WEBHOOK_INFO=$(gh api "repos/$REPO/hooks/$HOOK_ID" --jq '.config.url, .events, .active' 2>/dev/null || echo "")
if [ -n "$WEBHOOK_INFO" ]; then
    echo "   ✅ Webhook configured"
    RESULTS[config]="PASS"
else
    echo "   ⚠️  Could not fetch details (may need admin:repo_hook scope)"
    RESULTS[config]="WARN"
fi
echo ""

# Test 3: Recent deliveries
echo "3. Recent deliveries..."
gh api "repos/$REPO/hooks/$HOOK_ID/deliveries" --jq '.[0:3] | .[] | "\(.delivered_at) | \(.event) | HTTP \(.status_code)"' 2>/dev/null || echo "   ⚠️  Could not fetch history"
echo ""

# Test 4: WOS configuration
echo "4. WOS configuration..."
if [ -d "$REPO_ROOT/.workspace-os" ]; then
    echo "   ✅ .workspace-os/ exists"
    RESULTS[wos_dir]="PASS"
else
    echo "   ❌ Missing .workspace-os/"
    RESULTS[wos_dir]="FAIL"
fi

if [ -f "$REPO_ROOT/workspace.sources.json" ]; then
    echo "   ✅ workspace.sources.json exists"
    RESULTS[wos_file]="PASS"
else
    echo "   ⚠️  Missing workspace.sources.json"
    RESULTS[wos_file]="WARN"
fi
echo ""

# Test 5: Issue #864 label
echo "5. Issue #864 label check..."
LABELS=$(gh issue view 864 --repo "$REPO" --json labels --jq '.labels[].name' 2>/dev/null || echo "")
if echo "$LABELS" | grep -q "wos-review"; then
    echo "   ✅ Has wos-review label"
    RESULTS[label]="PASS"
else
    echo "   ❌ Missing wos-review label"
    RESULTS[label]="FAIL"
fi
echo ""

echo "=== Summary ==="
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

for key in "${!RESULTS[@]}"; do
    case "${RESULTS[$key]}" in
        PASS) ((PASS_COUNT++)) ;;
        FAIL) ((FAIL_COUNT++)) ;;
        WARN) ((WARN_COUNT++)) ;;
    esac
done

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "✅ Webhook operational"
    echo "✅ WOS configured"
else
    echo "⚠️  $FAIL_COUNT check(s) failed"
    echo "✅ $PASS_COUNT check(s) passed"
fi
echo "⚠️  Discord integration requires backend verification"

# Exit with error if any checks failed
[ "$FAIL_COUNT" -eq 0 ]
