#!/usr/bin/env bash
# Diagnostic tool for HomeDir autonomous SDLC worker
# Checks all critical dependencies and configuration

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FAILED=0
WARNINGS=0

echo "========================================"
echo "HomeDir SDLC Doctor v1.0"
echo "========================================"
echo ""

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    FAILED=$((FAILED + 1))
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
    WARNINGS=$((WARNINGS + 1))
}

# Check 1: GitHub CLI
echo "Checking GitHub CLI..."
if command -v gh &>/dev/null; then
    GH_VERSION=$(gh --version | head -1)
    check_pass "GitHub CLI installed: $GH_VERSION"

    # Check auth
    if gh auth status &>/dev/null; then
        GH_USER=$(gh api user -q .login 2>/dev/null || echo "unknown")
        check_pass "GitHub authenticated as: $GH_USER"
    else
        check_fail "GitHub CLI not authenticated (run: gh auth login)"
    fi
else
    check_fail "GitHub CLI not found in PATH"
fi
echo ""

# Check 2: SCC
echo "Checking SCC (AI agent)..."
if [[ -n "${SCC_BIN:-}" ]] && [[ -x "$SCC_BIN" ]]; then
    SCC_VERSION=$("$SCC_BIN" --version 2>/dev/null || echo "unknown")
    check_pass "SCC binary found: $SCC_BIN (version: $SCC_VERSION)"
else
    SCC_BIN="${SCC_BIN:-$(command -v scc 2>/dev/null || echo "")}"
    if [[ -x "$SCC_BIN" ]]; then
        SCC_VERSION=$("$SCC_BIN" --version 2>/dev/null || echo "unknown")
        check_pass "SCC binary found: $SCC_BIN (version: $SCC_VERSION)"
    else
        check_fail "SCC binary not found (check SCC_BIN environment variable)"
    fi
fi

# Check SCC config
ACTIVE_PROFILE=""
if ! command -v jq &>/dev/null; then
    check_fail "jq not found in PATH (required for SCC config parsing)"
else
SCC_CONFIG="${HOME}/.sc-agent/config.json"
if [[ -f "$SCC_CONFIG" ]]; then
    check_pass "SCC config found: $SCC_CONFIG"

    # Check active profile
    ACTIVE_PROFILE=$(jq -r '.activeProfile // "none"' "$SCC_CONFIG" 2>/dev/null || echo "parse-error")
    if [[ "$ACTIVE_PROFILE" != "none" && "$ACTIVE_PROFILE" != "parse-error" ]]; then
        check_pass "Active SCC profile: $ACTIVE_PROFILE"

        # Verify profile exists
        PROFILE_EXISTS=$(jq -r ".profiles.\"$ACTIVE_PROFILE\" | if . then \"yes\" else \"no\" end" "$SCC_CONFIG" 2>/dev/null || echo "no")
        if [[ "$PROFILE_EXISTS" == "yes" ]]; then
            MODEL=$(jq -r ".profiles.\"$ACTIVE_PROFILE\".model // \"unknown\"" "$SCC_CONFIG" 2>/dev/null)
            PROVIDER=$(jq -r ".profiles.\"$ACTIVE_PROFILE\".baseUrl // \"unknown\"" "$SCC_CONFIG" 2>/dev/null)
            check_pass "Model: $MODEL"
            check_pass "Provider: $PROVIDER"
        else
            check_fail "Active profile '$ACTIVE_PROFILE' not found in config"
        fi
    else
        check_warn "No active profile set in SCC config"
    fi
else
    check_fail "SCC config not found: $SCC_CONFIG"
fi
fi
echo ""

# Check 3: API Keys
echo "Checking API keys..."
if [[ -n "${SC_API_KEY:-}" ]]; then
    KEY_PREFIX="${SC_API_KEY:0:10}"
    check_pass "SC_API_KEY configured: ${KEY_PREFIX}..."

    # Check format matches profile
    if [[ "$ACTIVE_PROFILE" == *"rhoai"* ]]; then
        if [[ "$SC_API_KEY" == sk-* ]]; then
            check_pass "API key format matches RHOAI (sk-*)"
        else
            check_fail "RHOAI profile but API key doesn't start with 'sk-'"
        fi
    elif [[ "$ACTIVE_PROFILE" == *"nvidia"* ]]; then
        if [[ "$SC_API_KEY" == nvapi-* ]]; then
            check_pass "API key format matches NVIDIA (nvapi-*)"
        else
            check_fail "NVIDIA profile but API key doesn't start with 'nvapi-'"
        fi
    fi
else
    check_fail "SC_API_KEY not set"
fi
echo ""

# Check 4: Repository access
echo "Checking repository access..."
REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
if gh repo view "$REPO" &>/dev/null; then
    check_pass "Can access repository: $REPO"
else
    check_fail "Cannot access repository: $REPO"
fi
echo ""

# Check 5: Worktree and state directories
echo "Checking directories..."
WORKDIR="${HOMEDIR_SDLC_WORKDIR:-${HOME}/.local/share/homedir-sdlc/worktrees/homedir}"
STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-${HOME}/.local/state/homedir-sdlc}"
LOGFILE="${HOMEDIR_SDLC_LOGFILE:-${STATE_DIR}/logs/worker.log}"

# Check parent directories are writable
for DIR in "$(dirname "$WORKDIR")" "$STATE_DIR" "$(dirname "$LOGFILE")"; do
    if [[ -d "$DIR" ]]; then
        if [[ -w "$DIR" ]]; then
            check_pass "Directory writable: $DIR"
        else
            check_fail "Directory not writable: $DIR"
        fi
    else
        check_warn "Directory does not exist: $DIR (will be created on first run)"
    fi
done
echo ""

# Check 6: Systemd user service
echo "Checking systemd service..."
if systemctl --user is-active homedir-sdlc-worker.timer &>/dev/null; then
    check_pass "Worker timer is active"
else
    check_warn "Worker timer is not active (run: systemctl --user start homedir-sdlc-worker.timer)"
fi

if systemctl --user is-enabled homedir-sdlc-worker.timer &>/dev/null; then
    check_pass "Worker timer is enabled"
else
    check_warn "Worker timer is not enabled (run: systemctl --user enable homedir-sdlc-worker.timer)"
fi
echo ""

# Check 7: SCC provider connectivity
echo "Checking SCC provider connectivity..."
if [[ -x "$SCC_BIN" ]] && [[ -n "${SC_API_KEY:-}" ]]; then
    echo "Testing SCC with simple query..."
    TEMP_TEST=$(mktemp)
    if timeout 30 "$SCC_BIN" -yq "Say 'OK'" > "$TEMP_TEST" 2>&1; then
        RESPONSE=$(cat "$TEMP_TEST")
        if echo "$RESPONSE" | grep -qi "ok"; then
            check_pass "SCC provider responding correctly"
        else
            check_warn "SCC responded but output unexpected: ${RESPONSE:0:50}..."
        fi
    else
        ERROR=$(cat "$TEMP_TEST" | head -5)
        check_fail "SCC provider test failed: $ERROR"
    fi
    rm -f "$TEMP_TEST"
else
    check_warn "Skipping connectivity test (SCC binary or API key missing)"
fi
echo ""

# Check 8: Workflow of release (if gh workflow exists)
echo "Checking GitHub Actions workflows..."
if gh workflow list --repo "$REPO" 2>/dev/null | grep -q "Production Release"; then
    check_pass "Production Release workflow exists"
else
    check_warn "Production Release workflow not found (may not be critical)"
fi
echo ""

# Summary
echo "========================================"
echo "Summary:"
echo "========================================"
if [[ $FAILED -eq 0 ]]; then
    echo -e "${GREEN}All critical checks passed!${NC}"
    if [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}$WARNINGS warning(s) - review above${NC}"
    fi
    exit 0
else
    echo -e "${RED}$FAILED check(s) failed!${NC}"
    if [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}$WARNINGS warning(s)${NC}"
    fi
    echo ""
    echo "Fix the failed checks before running the SDLC worker."
    exit 1
fi
