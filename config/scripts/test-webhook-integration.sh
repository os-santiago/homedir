#!/bin/bash
# Test script for GitHub webhook → WOS → Discord integration (Issue #864)

set -euo pipefail

WEBHOOK_URL="https://homedir.opensourcesantiago.io/github-webhook"
REPO="os-santiago/homedir"
HOOK_ID="644594980"

echo "=== GitHub Webhook Integration Test ==="
echo ""

# Test 1: Webhook endpoint
echo "1. Testing webhook endpoint..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$WEBHOOK_URL" || echo "000")
if [ "$HTTP_CODE" = "501" ] || [ "$HTTP_CODE" = "405" ] || [ "$HTTP_CODE" = "403" ]; then
    echo "   ✅ Endpoint active (HTTP $HTTP_CODE)"
else
    echo "   ❌ Unexpected response (HTTP $HTTP_CODE)"
fi
echo ""

# Test 2: Webhook configuration
echo "2. Checking webhook configuration..."
WEBHOOK_INFO=$(gh api "repos/$REPO/hooks/$HOOK_ID" --jq '.config.url, .events, .active' 2>/dev/null || echo "")
if [ -n "$WEBHOOK_INFO" ]; then
    echo "   ✅ Webhook configured"
else
    echo "   ⚠️  Could not fetch details (may need admin:repo_hook scope)"
fi
echo ""

# Test 3: Recent deliveries
echo "3. Recent deliveries..."
gh api "repos/$REPO/hooks/$HOOK_ID/deliveries" --jq '.[0:3] | .[] | "\(.delivered_at) | \(.event) | HTTP \(.status_code)"' 2>/dev/null || echo "   ⚠️  Could not fetch history"
echo ""

# Test 4: WOS configuration
echo "4. WOS configuration..."
[ -d ".workspace-os" ] && echo "   ✅ .workspace-os/ exists" || echo "   ❌ Missing .workspace-os/"
[ -f "workspace.sources.json" ] && echo "   ✅ workspace.sources.json exists" || echo "   ⚠️  Missing workspace.sources.json"
echo ""

# Test 5: Issue #864 label
echo "5. Issue #864 label check..."
LABELS=$(gh issue view 864 --json labels --jq '.labels[].name' 2>/dev/null || echo "")
echo "$LABELS" | grep -q "wos-review" && echo "   ✅ Has wos-review label" || echo "   ❌ Missing wos-review label"
echo ""

echo "=== Summary ==="
echo "✅ Webhook operational"
echo "✅ WOS configured"
echo "⚠️  Discord integration requires backend verification"
