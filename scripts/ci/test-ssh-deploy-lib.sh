#!/usr/bin/env bash
# Test script for SSH Deploy Library
# Validates that all functions are exported and work correctly
#
# Usage: ./scripts/ci/test-ssh-deploy-lib.sh
#
# Related: #870

set -euo pipefail

# Source the library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/ssh-deploy-lib.sh
source "$SCRIPT_DIR/ssh-deploy-lib.sh"

echo "=== SSH Deploy Library Test Suite ==="
echo ""

# Test 1: Library version
echo "[TEST 1] Library version"
ssh_deploy_lib_version
echo "✓ PASSED"
echo ""

# Test 2: Validate required vars (should fail with missing vars)
echo "[TEST 2] Validate required vars (expect failure)"
if validate_required_vars DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_PRIVATE_KEY 2>/dev/null; then
  echo "✗ FAILED: Expected validation to fail with missing vars"
  exit 1
else
  echo "✓ PASSED: Validation correctly detected missing vars"
fi
echo ""

# Test 3: Validate StrictHostKeyChecking values
echo "[TEST 3] Validate StrictHostKeyChecking"
if ! validate_strict_host_key_checking "accept-new"; then
  echo "✗ FAILED: 'accept-new' should be valid"
  exit 1
fi
if ! validate_strict_host_key_checking "yes"; then
  echo "✗ FAILED: 'yes' should be valid"
  exit 1
fi
if ! validate_strict_host_key_checking "no" 2>/dev/null; then
  echo "✗ FAILED: 'no' should be valid (with warning)"
  exit 1
fi
if validate_strict_host_key_checking "invalid" 2>/dev/null; then
  echo "✗ FAILED: 'invalid' should be rejected"
  exit 1
fi
echo "✓ PASSED: StrictHostKeyChecking validation works"
echo ""

# Test 4: Validate required vars (should pass with vars set)
echo "[TEST 4] Validate required vars (expect success)"
export DEPLOY_HOST="test.example.com"
export DEPLOY_USER="testuser"
export DEPLOY_SSH_PRIVATE_KEY="fake-key"
if ! validate_required_vars DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_PRIVATE_KEY; then
  echo "✗ FAILED: Validation should pass with vars set"
  exit 1
fi
echo "✓ PASSED: Validation passed with all vars set"
echo ""

# Test 5: Function exports
echo "[TEST 5] Verify function exports"
required_functions=(
  "validate_required_vars"
  "validate_strict_host_key_checking"
  "retry_with_backoff"
  "ssh_setup_known_hosts"
  "ssh_exec"
  "scp_push"
  "scp_pull"
  "ssh_test_connectivity"
  "ssh_deploy_lib_version"
)

for func in "${required_functions[@]}"; do
  if ! type -t "$func" | grep -q "function"; then
    echo "✗ FAILED: Function '$func' not exported"
    exit 1
  fi
done
echo "✓ PASSED: All required functions exported"
echo ""

echo "=== ALL TESTS PASSED ===" echo ""
echo "Note: This test validates library loading and basic validation."
echo "Integration tests with real SSH connections should be run in CI."
