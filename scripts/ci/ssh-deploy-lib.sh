#!/usr/bin/env bash
# SSH Deploy Library
# Provides standardized SSH deployment functions for GitHub Actions workflows
#
# Usage:
#   source scripts/ci/ssh-deploy-lib.sh
#   ssh_setup_known_hosts "$DEPLOY_HOST" "$DEPLOY_PORT" "$DEPLOY_SSH_KNOWN_HOSTS"
#   ssh_exec "systemctl status homedir" "$DEPLOY_HOST" "$DEPLOY_USER" "$DEPLOY_PORT"
#   scp_push "local-file.tgz" "/remote/path/file.tgz" "$DEPLOY_HOST" "$DEPLOY_USER" "$DEPLOY_PORT"
#
# Required Environment Variables:
#   - DEPLOY_HOST: Target host for deployment
#   - DEPLOY_USER: SSH user for deployment
#   - DEPLOY_SSH_PRIVATE_KEY: SSH private key (handled by webfactory/ssh-agent)
#
# Optional Environment Variables:
#   - DEPLOY_PORT: SSH port (default: 22)
#   - DEPLOY_SSH_STRICT_HOST_KEY_CHECKING: StrictHostKeyChecking mode (default: accept-new)
#   - DEPLOY_SSH_KNOWN_HOSTS: Pre-configured known_hosts content
#
# Security:
#   - StrictHostKeyChecking defaults to "accept-new" (NEVER "no")
#   - Only allows "accept-new", "yes", or "no" (warns if "no")
#   - Retries with exponential backoff for transient failures
#
# Related Issue: #870
# Related Workflows: release.yml, cfp-go-live-resilience.yml

set -euo pipefail

# Color codes for output
readonly RED='\033[0;31m'
readonly YELLOW='\033[1;33m'
readonly GREEN='\033[0;32m'
readonly NC='\033[0m' # No Color

# Default configuration
readonly DEFAULT_PORT=22
readonly DEFAULT_STRICT="accept-new"
readonly MAX_RETRIES=3
readonly RETRY_BASE_DELAY=5

# =============================================================================
# VALIDATION FUNCTIONS
# =============================================================================

# validate_required_vars: Validate that required environment variables are set
# Usage: validate_required_vars DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_PRIVATE_KEY
validate_required_vars() {
  local missing_vars=()

  for var in "$@"; do
    if [ -z "${!var:-}" ]; then
      missing_vars+=("$var")
    fi
  done

  if [ ${#missing_vars[@]} -gt 0 ]; then
    echo -e "${RED}ERROR: Missing required environment variables:${NC}" >&2
    for var in "${missing_vars[@]}"; do
      echo "  - $var" >&2
    done
    return 1
  fi

  return 0
}

# validate_strict_host_key_checking: Validate and warn about StrictHostKeyChecking value
# Usage: validate_strict_host_key_checking "$strict_value"
# Returns: 0 if valid, 1 if invalid
validate_strict_host_key_checking() {
  local strict="${1:-$DEFAULT_STRICT}"

  case "$strict" in
    accept-new|yes)
      return 0
      ;;
    no)
      echo -e "${YELLOW}WARNING: StrictHostKeyChecking=no is INSECURE and bypasses host verification.${NC}" >&2
      echo -e "${YELLOW}WARNING: This should ONLY be used in isolated test environments.${NC}" >&2
      return 0
      ;;
    *)
      echo -e "${RED}ERROR: Invalid StrictHostKeyChecking value: $strict${NC}" >&2
      echo "Valid values: accept-new, yes, no" >&2
      return 1
      ;;
  esac
}

# =============================================================================
# RETRY LOGIC
# =============================================================================

# retry_with_backoff: Execute command with exponential backoff retry
# Usage: retry_with_backoff <max_attempts> <base_delay> <command> [args...]
# Example: retry_with_backoff 3 5 ssh user@host "ls -la"
retry_with_backoff() {
  local max_attempts="$1"
  local base_delay="$2"
  shift 2
  local cmd=("$@")

  local attempt=1
  local delay="$base_delay"

  while [ "$attempt" -le "$max_attempts" ]; do
    echo "Attempt $attempt/$max_attempts: ${cmd[*]}" >&2

    if "${cmd[@]}"; then
      return 0
    fi

    local exit_code=$?

    if [ "$attempt" -eq "$max_attempts" ]; then
      echo -e "${RED}ERROR: Command failed after $max_attempts attempts${NC}" >&2
      return "$exit_code"
    fi

    echo -e "${YELLOW}Command failed (exit code: $exit_code). Retrying in ${delay}s...${NC}" >&2
    sleep "$delay"

    # Exponential backoff: delay *= 2
    delay=$((delay * 2))
    attempt=$((attempt + 1))
  done

  return 1
}

# =============================================================================
# SSH SETUP FUNCTIONS
# =============================================================================

# ssh_setup_known_hosts: Configure SSH known_hosts for target host
# Usage: ssh_setup_known_hosts <host> [port] [known_hosts_content]
# Arguments:
#   host: Target hostname or IP
#   port: SSH port (default: 22)
#   known_hosts_content: Pre-configured known_hosts content (optional)
#
# If known_hosts_content is provided, it will be used directly.
# Otherwise, ssh-keyscan will be used to populate known_hosts.
ssh_setup_known_hosts() {
  local host="${1:?Missing required argument: host}"
  local port="${2:-$DEFAULT_PORT}"
  local known_hosts_content="${3:-}"

  validate_required_vars DEPLOY_HOST

  # Create ~/.ssh if it doesn't exist
  mkdir -p ~/.ssh
  chmod 700 ~/.ssh

  # If known_hosts content is provided, use it directly
  if [ -n "$known_hosts_content" ]; then
    echo "Using pre-configured known_hosts content"
    printf '%s\n' "$known_hosts_content" >> ~/.ssh/known_hosts
    chmod 644 ~/.ssh/known_hosts
    echo -e "${GREEN}✓ SSH known_hosts configured from provided content${NC}"
    return 0
  fi

  # Otherwise, use ssh-keyscan with retry
  echo "Populating known_hosts using ssh-keyscan for ${host}:${port}"

  if retry_with_backoff "$MAX_RETRIES" "$RETRY_BASE_DELAY" \
    ssh-keyscan -T 5 -p "$port" "$host" >> ~/.ssh/known_hosts 2>/dev/null; then
    chmod 644 ~/.ssh/known_hosts
    echo -e "${GREEN}✓ SSH known_hosts populated from ssh-keyscan${NC}"
    return 0
  else
    echo -e "${YELLOW}WARNING: ssh-keyscan failed for ${host}:${port}${NC}" >&2
    echo -e "${YELLOW}WARNING: Continuing with StrictHostKeyChecking setting${NC}" >&2
    chmod 644 ~/.ssh/known_hosts 2>/dev/null || true
    return 0  # Non-fatal: StrictHostKeyChecking will handle this
  fi
}

# =============================================================================
# SSH EXECUTION FUNCTIONS
# =============================================================================

# ssh_exec: Execute command on remote host via SSH
# Usage: ssh_exec <command> <host> <user> [port] [strict_mode]
# Arguments:
#   command: Remote command to execute
#   host: Target hostname or IP
#   user: SSH username
#   port: SSH port (default: 22)
#   strict_mode: StrictHostKeyChecking mode (default: accept-new)
#
# Returns: Exit code of remote command
ssh_exec() {
  local command="${1:?Missing required argument: command}"
  local host="${2:?Missing required argument: host}"
  local user="${3:?Missing required argument: user}"
  local port="${4:-$DEFAULT_PORT}"
  local strict="${5:-${DEPLOY_SSH_STRICT_HOST_KEY_CHECKING:-$DEFAULT_STRICT}}"

  validate_required_vars DEPLOY_HOST DEPLOY_USER
  validate_strict_host_key_checking "$strict" || return 1

  # Build SSH options array
  local ssh_opts=("-p" "$port" "-o" "StrictHostKeyChecking=${strict}")

  # If StrictHostKeyChecking=no, disable known_hosts
  if [ "$strict" = "no" ]; then
    ssh_opts=("-p" "$port" "-o" "StrictHostKeyChecking=no" "-o" "UserKnownHostsFile=/dev/null")
  fi

  echo "Executing SSH command on ${user}@${host}:${port}"
  echo "Command: $command"

  retry_with_backoff "$MAX_RETRIES" "$RETRY_BASE_DELAY" \
    ssh "${ssh_opts[@]}" "${user}@${host}" "$command"
}

# =============================================================================
# SCP FILE TRANSFER FUNCTIONS
# =============================================================================

# scp_push: Copy local file to remote host via SCP
# Usage: scp_push <local_file> <remote_path> <host> <user> [port] [strict_mode]
# Arguments:
#   local_file: Local file path to transfer
#   remote_path: Remote destination path
#   host: Target hostname or IP
#   user: SSH username
#   port: SSH port (default: 22)
#   strict_mode: StrictHostKeyChecking mode (default: accept-new)
#
# Returns: 0 on success, non-zero on failure
scp_push() {
  local local_file="${1:?Missing required argument: local_file}"
  local remote_path="${2:?Missing required argument: remote_path}"
  local host="${3:?Missing required argument: host}"
  local user="${4:?Missing required argument: user}"
  local port="${5:-$DEFAULT_PORT}"
  local strict="${6:-${DEPLOY_SSH_STRICT_HOST_KEY_CHECKING:-$DEFAULT_STRICT}}"

  validate_required_vars DEPLOY_HOST DEPLOY_USER
  validate_strict_host_key_checking "$strict" || return 1

  # Validate local file exists
  if [ ! -f "$local_file" ]; then
    echo -e "${RED}ERROR: Local file not found: $local_file${NC}" >&2
    return 1
  fi

  # Build SCP options array
  local scp_opts=("-P" "$port" "-o" "StrictHostKeyChecking=${strict}")

  # If StrictHostKeyChecking=no, disable known_hosts
  if [ "$strict" = "no" ]; then
    scp_opts=("-P" "$port" "-o" "StrictHostKeyChecking=no" "-o" "UserKnownHostsFile=/dev/null")
  fi

  echo "Transferring file via SCP: $local_file → ${user}@${host}:${remote_path}"

  retry_with_backoff "$MAX_RETRIES" "$RETRY_BASE_DELAY" \
    scp "${scp_opts[@]}" "$local_file" "${user}@${host}:${remote_path}"
}

# scp_pull: Copy remote file to local host via SCP
# Usage: scp_pull <remote_path> <local_file> <host> <user> [port] [strict_mode]
# Arguments:
#   remote_path: Remote file path to transfer
#   local_file: Local destination path
#   host: Target hostname or IP
#   user: SSH username
#   port: SSH port (default: 22)
#   strict_mode: StrictHostKeyChecking mode (default: accept-new)
#
# Returns: 0 on success, non-zero on failure
scp_pull() {
  local remote_path="${1:?Missing required argument: remote_path}"
  local local_file="${2:?Missing required argument: local_file}"
  local host="${3:?Missing required argument: host}"
  local user="${4:?Missing required argument: user}"
  local port="${5:-$DEFAULT_PORT}"
  local strict="${6:-${DEPLOY_SSH_STRICT_HOST_KEY_CHECKING:-$DEFAULT_STRICT}}"

  validate_required_vars DEPLOY_HOST DEPLOY_USER
  validate_strict_host_key_checking "$strict" || return 1

  # Build SCP options array
  local scp_opts=("-P" "$port" "-o" "StrictHostKeyChecking=${strict}")

  # If StrictHostKeyChecking=no, disable known_hosts
  if [ "$strict" = "no" ]; then
    scp_opts=("-P" "$port" "-o" "StrictHostKeyChecking=no" "-o" "UserKnownHostsFile=/dev/null")
  fi

  echo "Fetching file via SCP: ${user}@${host}:${remote_path} → $local_file"

  retry_with_backoff "$MAX_RETRIES" "$RETRY_BASE_DELAY" \
    scp "${scp_opts[@]}" "${user}@${host}:${remote_path}" "$local_file"
}

# =============================================================================
# CONVENIENCE FUNCTIONS
# =============================================================================

# ssh_test_connectivity: Test SSH connectivity to target host
# Usage: ssh_test_connectivity [host] [user] [port]
# Returns: 0 if connection successful, non-zero otherwise
ssh_test_connectivity() {
  local host="${1:-${DEPLOY_HOST:?Missing DEPLOY_HOST}}"
  local user="${2:-${DEPLOY_USER:?Missing DEPLOY_USER}}"
  local port="${3:-${DEPLOY_PORT:-$DEFAULT_PORT}}"

  echo "Testing SSH connectivity to ${user}@${host}:${port}"

  if ssh_exec "echo 'SSH connectivity OK'" "$host" "$user" "$port"; then
    echo -e "${GREEN}✓ SSH connectivity test PASSED${NC}"
    return 0
  else
    echo -e "${RED}✗ SSH connectivity test FAILED${NC}" >&2
    return 1
  fi
}

# =============================================================================
# LIBRARY INFO
# =============================================================================

# ssh_deploy_lib_version: Print library version and usage
ssh_deploy_lib_version() {
  cat <<EOF
SSH Deploy Library v1.0.0
Issue: #870 - Standardize SSH deploy handling across workflows

Functions:
  - validate_required_vars         Validate required environment variables
  - ssh_setup_known_hosts          Configure SSH known_hosts
  - ssh_exec                       Execute remote command via SSH
  - scp_push                       Copy local file to remote host
  - scp_pull                       Copy remote file to local host
  - ssh_test_connectivity          Test SSH connection

Environment Variables:
  Required: DEPLOY_HOST, DEPLOY_USER, DEPLOY_SSH_PRIVATE_KEY
  Optional: DEPLOY_PORT, DEPLOY_SSH_STRICT_HOST_KEY_CHECKING, DEPLOY_SSH_KNOWN_HOSTS

Security:
  - StrictHostKeyChecking defaults to "accept-new" (never "no")
  - Retry logic with exponential backoff (3 attempts, 5s base delay)
  - Host key verification via known_hosts

Usage Example:
  source scripts/ci/ssh-deploy-lib.sh
  validate_required_vars DEPLOY_HOST DEPLOY_USER
  ssh_setup_known_hosts "\$DEPLOY_HOST" "\$DEPLOY_PORT" "\$DEPLOY_SSH_KNOWN_HOSTS"
  ssh_test_connectivity
  scp_push "myapp.tgz" "/tmp/myapp.tgz" "\$DEPLOY_HOST" "\$DEPLOY_USER" "\$DEPLOY_PORT"
  ssh_exec "tar -xzf /tmp/myapp.tgz && systemctl restart myapp" "\$DEPLOY_HOST" "\$DEPLOY_USER"
EOF
}

# Export functions for use in workflows
export -f validate_required_vars
export -f validate_strict_host_key_checking
export -f retry_with_backoff
export -f ssh_setup_known_hosts
export -f ssh_exec
export -f scp_push
export -f scp_pull
export -f ssh_test_connectivity
export -f ssh_deploy_lib_version

echo -e "${GREEN}✓ SSH Deploy Library loaded successfully${NC}"
