#!/bin/bash
# SDLC E2E Test Suite
# Comprehensive test suite with multiple test scenarios

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCHESTRATOR="${SCRIPT_DIR}/sdlc-e2e-orchestrator.sh"
VALIDATOR="${SCRIPT_DIR}/phase-validators.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test results
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
TEST_RESULTS=()

log() {
  echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"
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

record_result() {
  local test_name="$1"
  local result="$2"
  local details="${3:-}"

  TESTS_RUN=$((TESTS_RUN + 1))

  if [[ "${result}" == "PASS" ]]; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
    TEST_RESULTS+=("✓ ${test_name}: ${GREEN}PASS${NC} ${details}")
  else
    TESTS_FAILED=$((TESTS_FAILED + 1))
    TEST_RESULTS+=("✗ ${test_name}: ${RED}FAIL${NC} ${details}")
  fi
}

# Test Case 1: Simple documentation update
test_simple_doc_update() {
  log "Running Test 1: Simple Documentation Update"

  if "${ORCHESTRATOR}" simple; then
    record_result "Simple Doc Update" "PASS" "(expected: 5-10 min)"
    return 0
  else
    record_result "Simple Doc Update" "FAIL"
    return 1
  fi
}

# Test Case 2: Medium complexity bug fix
test_medium_bug_fix() {
  log "Running Test 2: Medium Complexity Bug Fix"

  if "${ORCHESTRATOR}" medium; then
    record_result "Medium Bug Fix" "PASS" "(expected: 15-30 min)"
    return 0
  else
    record_result "Medium Bug Fix" "FAIL"
    return 1
  fi
}

# Test Case 3: Complex refactoring
test_complex_refactor() {
  log "Running Test 3: Complex Refactoring"

  if "${ORCHESTRATOR}" complex; then
    record_result "Complex Refactor" "PASS" "(expected: 30-60 min)"
    return 0
  else
    record_result "Complex Refactor" "FAIL"
    return 1
  fi
}

# Test Case 4: Custom test - Add console.log
test_add_console_log() {
  log "Running Test 4: Add Console Log Statement"

  local title="[e2e-test-suite] Add console.log to trending scraper"
  local body="Add a console.log statement at the start of the getTrendingRepos function in quarkus-app/src/main/webui/src/services/github.ts that logs 'Fetching trending repos'"

  if "${ORCHESTRATOR}" custom --title "${title}" --body "${body}"; then
    record_result "Add Console Log" "PASS"
    return 0
  else
    record_result "Add Console Log" "FAIL"
    return 1
  fi
}

# Test Case 5: Custom test - Fix typo
test_fix_typo() {
  log "Running Test 5: Fix Documentation Typo"

  local title="[e2e-test-suite] Fix typo in README"
  local body="Fix the typo 'developement' → 'development' in the README.md file"

  if "${ORCHESTRATOR}" custom --title "${title}" --body "${body}"; then
    record_result "Fix Typo" "PASS"
    return 0
  else
    record_result "Fix Typo" "FAIL"
    return 1
  fi
}

# Test Case 6: Custom test - Add health check endpoint
test_add_health_check() {
  log "Running Test 6: Add Health Check Endpoint"

  local title="[e2e-test-suite] Add /api/health endpoint"
  local body="Add a new REST endpoint at /api/health in quarkus-app that returns {\"status\":\"UP\",\"timestamp\":\"<current-time>\"} with status 200"

  if "${ORCHESTRATOR}" custom --title "${title}" --body "${body}"; then
    record_result "Add Health Check" "PASS"
    return 0
  else
    record_result "Add Health Check" "FAIL"
    return 1
  fi
}

# Smoke test - validate environment
smoke_test() {
  log "Running smoke tests..."

  local all_pass=true

  # Check gh CLI
  if command -v gh &>/dev/null; then
    success "gh CLI is installed"
  else
    error "gh CLI is not installed"
    all_pass=false
  fi

  # Check jq
  if command -v jq &>/dev/null; then
    success "jq is installed"
  else
    error "jq is not installed"
    all_pass=false
  fi

  # Check gh auth
  if gh auth status &>/dev/null; then
    success "gh CLI is authenticated"
  else
    error "gh CLI is not authenticated"
    all_pass=false
  fi

  # Check orchestrator
  if [[ -x "${ORCHESTRATOR}" ]]; then
    success "Orchestrator script is executable"
  else
    error "Orchestrator script is not executable"
    chmod +x "${ORCHESTRATOR}" 2>/dev/null || true
  fi

  # Check validator
  if [[ -x "${VALIDATOR}" ]]; then
    success "Validator script is executable"
  else
    error "Validator script is not executable"
    chmod +x "${VALIDATOR}" 2>/dev/null || true
  fi

  if [[ "${all_pass}" == "true" ]]; then
    success "Smoke tests passed"
    return 0
  else
    error "Smoke tests failed"
    return 1
  fi
}

# Print summary
print_summary() {
  echo ""
  echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║                    TEST SUITE SUMMARY                          ║${NC}"
  echo -e "${BLUE}╠════════════════════════════════════════════════════════════════╣${NC}"
  printf "${BLUE}║${NC} Total Tests:  %-45s ${BLUE}║${NC}\n" "${TESTS_RUN}"
  printf "${BLUE}║${NC} Passed:       ${GREEN}%-45s${NC} ${BLUE}║${NC}\n" "${TESTS_PASSED}"
  printf "${BLUE}║${NC} Failed:       ${RED}%-45s${NC} ${BLUE}║${NC}\n" "${TESTS_FAILED}"
  echo -e "${BLUE}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${BLUE}║ Test Results:                                                  ║${NC}"

  for result in "${TEST_RESULTS[@]}"; do
    echo -e "${BLUE}║${NC} ${result}"
  done

  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  if [[ ${TESTS_FAILED} -eq 0 ]]; then
    success "All tests passed!"
    return 0
  else
    error "${TESTS_FAILED} test(s) failed"
    return 1
  fi
}

show_usage() {
  cat <<EOF
Usage: $0 [OPTIONS] [TEST_SUITE]

Test Suites:
  smoke       - Run smoke tests only
  quick       - Run quick tests (simple + one custom)
  standard    - Run standard test suite (simple + medium + 2 custom)
  full        - Run all tests (simple + medium + complex + all custom)
  custom      - Run custom tests only

Options:
  --help      - Show this help message

Examples:
  # Run smoke tests
  $0 smoke

  # Run quick test suite
  $0 quick

  # Run full test suite
  $0 full

EOF
}

main() {
  local suite="standard"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help)
        show_usage
        exit 0
        ;;
      smoke|quick|standard|full|custom)
        suite="$1"
        shift
        ;;
      *)
        error "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║              SDLC E2E TEST SUITE EXECUTION                     ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║ Suite: ${suite}${NC}"
  echo -e "${GREEN}║ Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  # Always run smoke tests first
  if ! smoke_test; then
    error "Smoke tests failed - aborting"
    exit 1
  fi

  echo ""

  case "${suite}" in
    smoke)
      # Only smoke tests
      ;;
    quick)
      test_simple_doc_update || true
      test_add_console_log || true
      ;;
    standard)
      test_simple_doc_update || true
      test_medium_bug_fix || true
      test_add_console_log || true
      test_fix_typo || true
      ;;
    full)
      test_simple_doc_update || true
      test_medium_bug_fix || true
      test_complex_refactor || true
      test_add_console_log || true
      test_fix_typo || true
      test_add_health_check || true
      ;;
    custom)
      test_add_console_log || true
      test_fix_typo || true
      test_add_health_check || true
      ;;
  esac

  print_summary
}

main "$@"
