#!/bin/bash
# SDLC E2E Test Suite - Quick Setup
# Validates environment and prepares for testing

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
  echo -e "${BLUE}[SETUP]${NC} $*"
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

check_command() {
  local cmd="$1"
  local install_hint="${2:-}"

  if command -v "${cmd}" &>/dev/null; then
    success "${cmd} is installed"
    return 0
  else
    error "${cmd} is not installed"
    if [[ -n "${install_hint}" ]]; then
      echo "  Install: ${install_hint}"
    fi
    return 1
  fi
}

check_gh_auth() {
  if gh auth status &>/dev/null; then
    success "GitHub CLI is authenticated"

    local user
    user=$(gh api user --jq '.login' 2>/dev/null || echo "unknown")
    log "Authenticated as: ${user}"
    return 0
  else
    error "GitHub CLI is not authenticated"
    echo "  Run: gh auth login"
    return 1
  fi
}

check_repo_access() {
  local repo="${1:-os-santiago/homedir}"

  log "Checking repository access: ${repo}"

  if gh repo view "${repo}" &>/dev/null; then
    success "Repository ${repo} is accessible"
    return 0
  else
    error "Cannot access repository ${repo}"
    return 1
  fi
}

make_scripts_executable() {
  log "Making scripts executable..."

  local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

  chmod +x "${script_dir}"/*.sh 2>/dev/null || true

  success "Scripts are executable"
}

check_dashboard_api() {
  local url="${1:-http://localhost:8080}"

  log "Checking dashboard API at ${url}..."

  if curl -sf "${url}/q/health" >/dev/null 2>&1; then
    success "Dashboard API is accessible at ${url}"
    return 0
  else
    warn "Dashboard API is not accessible at ${url}"
    echo "  This is optional - tests can still run without it"
    return 1
  fi
}

create_env_example() {
  local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local env_file="${script_dir}/.env.example"

  if [[ -f "${env_file}" ]]; then
    return 0
  fi

  cat > "${env_file}" <<'EOF'
# SDLC E2E Test Suite - Environment Variables

# Repository
export HOMEDIR_SDLC_REPO="os-santiago/homedir"

# Timeouts (seconds)
export E2E_TIMEOUT_ADMISSION=60
export E2E_TIMEOUT_QUEUE=180
export E2E_TIMEOUT_RUNNING=1800
export E2E_TIMEOUT_PR_CREATION=120
export E2E_TIMEOUT_CHECKS=600
export E2E_TIMEOUT_MERGE=300
export E2E_TIMEOUT_DEPLOYMENT=600

# Dashboard
export SDLC_DASHBOARD_URL="http://localhost:8080"
export E2E_DASHBOARD_REFRESH=5

# VPS (optional - for advanced validation)
# export SDLC_VPS_HOST="homedir-sdlc@72.60.141.165"
EOF

  success "Created .env.example"
}

run_smoke_test() {
  log "Running smoke test..."

  local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local test_script="${script_dir}/run-e2e-test.sh"

  if [[ ! -x "${test_script}" ]]; then
    error "run-e2e-test.sh is not executable"
    return 1
  fi

  # Try to list issues
  if "${test_script}" --list >/dev/null 2>&1; then
    success "Smoke test passed - can list issues"
    return 0
  else
    warn "Smoke test failed - check GitHub auth and repo access"
    return 1
  fi
}

print_summary() {
  echo ""
  echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║              SETUP COMPLETE - READY TO TEST                    ║${NC}"
  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "${GREEN}Next steps:${NC}"
  echo ""
  echo "1. List eligible issues:"
  echo "   ${BLUE}./run-e2e-test.sh --list${NC}"
  echo ""
  echo "2. Run E2E test on an issue:"
  echo "   ${BLUE}./run-e2e-test.sh <issue_number>${NC}"
  echo ""
  echo "3. Monitor dashboard (optional):"
  echo "   ${BLUE}./monitor-dashboard.sh watch${NC}"
  echo ""
  echo "4. Read the docs:"
  echo "   ${BLUE}cat USAGE.md${NC}"
  echo ""
}

main() {
  echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║         SDLC E2E Test Suite - Environment Setup               ║${NC}"
  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  local all_ok=true

  # Check required commands
  log "Checking required dependencies..."
  check_command "gh" "brew install gh (macOS) or apt-get install gh (Linux)" || all_ok=false
  check_command "jq" "brew install jq (macOS) or apt-get install jq (Linux)" || all_ok=false
  check_command "curl" "Usually pre-installed" || all_ok=false

  echo ""

  # Check GitHub auth
  log "Checking GitHub authentication..."
  check_gh_auth || all_ok=false

  echo ""

  # Check repo access
  log "Checking repository access..."
  check_repo_access "os-santiago/homedir" || all_ok=false

  echo ""

  # Make scripts executable
  make_scripts_executable

  echo ""

  # Create env example
  create_env_example

  echo ""

  # Check dashboard (optional)
  check_dashboard_api "http://localhost:8080" || true

  echo ""

  # Run smoke test
  if [[ "${all_ok}" == "true" ]]; then
    run_smoke_test || all_ok=false
  fi

  echo ""

  if [[ "${all_ok}" == "true" ]]; then
    print_summary
    return 0
  else
    error "Setup incomplete - fix the issues above and run again"
    return 1
  fi
}

main "$@"
