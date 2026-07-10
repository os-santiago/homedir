#!/usr/bin/env bash
# Worker Health Check & Auto-Recovery
#
# Monitors worker health and automatically recovers from common failure modes:
# - Stale heartbeat (worker hung/crashed)
# - Stuck lock files (worker interrupted)
# - Missing dependencies (gh/scc not found)
# - Permission errors (directories not writable)
#
# Exit codes:
#   0 - Healthy
#   1 - Unhealthy (auto-recovery attempted)
#   2 - Critical (manual intervention required)

set -euo pipefail

# Load environment if available
ENV_FILE="${HOMEDIR_SDLC_ENV_FILE:-${HOME}/.config/homedir-sdlc/env}"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-${HOME}/.local/state/homedir-sdlc}"
LOGFILE="${HOMEDIR_SDLC_LOGFILE:-${STATE_DIR}/logs/worker.log}"
HEARTBEAT_FILE="${STATE_DIR}/heartbeat.json"
LOCK_FILE="${STATE_DIR}/worker.lock"
WORKER_SCRIPT="${HOMEDIR_SDLC_WORKER_SCRIPT:-${HOME}/.local/bin/homedir-sdlc-worker.sh}"

# Thresholds
HEARTBEAT_MAX_AGE=900      # 15 minutes
LOCK_FILE_MAX_AGE=600      # 10 minutes
LOG_MAX_AGE=1800           # 30 minutes

# Recovery actions taken
RECOVERY_ACTIONS=()

log() {
  local level="$1"
  shift
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [$level] $*" >&2
}

info() {
  log "INFO" "$@"
}

warn() {
  log "WARN" "$@"
}

error() {
  log "ERROR" "$@"
}

# Check if heartbeat file exists and is fresh
check_heartbeat() {
  info "Checking heartbeat..."

  if [[ ! -f "${HEARTBEAT_FILE}" ]]; then
    warn "Heartbeat file missing: ${HEARTBEAT_FILE}"
    return 1
  fi

  # Check if file is valid JSON
  if ! jq -e . "${HEARTBEAT_FILE}" >/dev/null 2>&1; then
    warn "Heartbeat file is not valid JSON"
    return 1
  fi

  # Get timestamp (try both .timestamp and .updated_at for compatibility)
  local last_update
  last_update=$(jq -r '.timestamp // .updated_at // 0' "${HEARTBEAT_FILE}" 2>/dev/null || echo "0")

  if [[ "$last_update" == "0" || "$last_update" == "null" ]]; then
    warn "Heartbeat timestamp missing or invalid"
    return 1
  fi

  # Convert ISO 8601 to Unix timestamp if needed
  if [[ "$last_update" =~ [TZ] ]]; then
    last_update=$(date -d "$last_update" +%s 2>/dev/null || echo "0")
  fi

  # Calculate age
  local now
  now=$(date +%s)
  local age=$((now - last_update))

  if [[ $age -gt $HEARTBEAT_MAX_AGE ]]; then
    warn "Heartbeat stale: ${age}s old (max: ${HEARTBEAT_MAX_AGE}s)"
    return 1
  fi

  info "Heartbeat OK: ${age}s old"
  return 0
}

# Check for stuck lock file
check_lock_file() {
  info "Checking lock file..."

  if [[ ! -f "${LOCK_FILE}" ]]; then
    info "No lock file (worker idle)"
    return 0
  fi

  # Check file age
  local lock_age
  if [[ -e "${LOCK_FILE}" ]]; then
    lock_age=$(( $(date +%s) - $(stat -c %Y "${LOCK_FILE}" 2>/dev/null || stat -f %m "${LOCK_FILE}" 2>/dev/null || echo 0) ))
  else
    lock_age=0
  fi

  if [[ $lock_age -gt $LOCK_FILE_MAX_AGE ]]; then
    warn "Lock file stale: ${lock_age}s old (max: ${LOCK_FILE_MAX_AGE}s)"

    # Auto-recovery: Remove stale lock file
    if rm -f "${LOCK_FILE}" 2>/dev/null; then
      info "AUTO-RECOVERY: Removed stale lock file"
      RECOVERY_ACTIONS+=("removed_stale_lock")
      return 0
    else
      error "Failed to remove stale lock file"
      return 1
    fi
  fi

  info "Lock file OK: ${lock_age}s old"
  return 0
}

# Check required commands
check_dependencies() {
  info "Checking dependencies..."

  local missing=()

  if ! command -v gh >/dev/null 2>&1; then
    missing+=("gh")
  fi

  if ! command -v jq >/dev/null 2>&1; then
    missing+=("jq")
  fi

  if ! command -v git >/dev/null 2>&1; then
    missing+=("git")
  fi

  # Check scc (may be in custom location)
  local scc_bin="${SCC_BIN:-scc}"
  if ! command -v "$scc_bin" >/dev/null 2>&1; then
    missing+=("scc")
  fi

  if [[ ${#missing[@]} -gt 0 ]]; then
    error "Missing dependencies: ${missing[*]}"
    return 2  # Critical - requires manual intervention
  fi

  info "All dependencies present"
  return 0
}

# Check directory permissions
check_permissions() {
  info "Checking directory permissions..."

  local dirs=(
    "${STATE_DIR}"
    "${STATE_DIR}/logs"
    "${STATE_DIR}/issues"
    "${STATE_DIR}/prs"
  )

  for dir in "${dirs[@]}"; do
    if [[ ! -d "$dir" ]]; then
      warn "Directory missing: $dir"

      # Auto-recovery: Create missing directory
      if mkdir -p "$dir" 2>/dev/null; then
        info "AUTO-RECOVERY: Created directory $dir"
        RECOVERY_ACTIONS+=("created_dir:$dir")
      else
        error "Failed to create directory: $dir"
        return 2  # Critical
      fi
    fi

    if [[ ! -w "$dir" ]]; then
      error "Directory not writable: $dir"
      return 2  # Critical - requires manual intervention
    fi
  done

  info "All directories accessible"
  return 0
}

# Check recent activity in logs
check_log_activity() {
  info "Checking log activity..."

  if [[ ! -f "${LOGFILE}" ]]; then
    warn "Log file missing: ${LOGFILE}"
    return 1
  fi

  # Check log file age (last modification)
  local log_age
  if [[ -e "${LOGFILE}" ]]; then
    log_age=$(( $(date +%s) - $(stat -c %Y "${LOGFILE}" 2>/dev/null || stat -f %m "${LOGFILE}" 2>/dev/null || echo 0) ))
  else
    log_age=9999
  fi

  if [[ $log_age -gt $LOG_MAX_AGE ]]; then
    warn "Log file stale: ${log_age}s since last write (max: ${LOG_MAX_AGE}s)"
    return 1
  fi

  info "Log activity OK: ${log_age}s since last write"
  return 0
}

# Check worker script exists and is executable
check_worker_script() {
  info "Checking worker script..."

  if [[ ! -f "${WORKER_SCRIPT}" ]]; then
    error "Worker script not found: ${WORKER_SCRIPT}"
    return 2  # Critical
  fi

  if [[ ! -x "${WORKER_SCRIPT}" ]]; then
    warn "Worker script not executable: ${WORKER_SCRIPT}"

    # Auto-recovery: Make script executable
    if chmod +x "${WORKER_SCRIPT}" 2>/dev/null; then
      info "AUTO-RECOVERY: Made worker script executable"
      RECOVERY_ACTIONS+=("chmod_worker_script")
      return 0
    else
      error "Failed to make worker script executable"
      return 2  # Critical
    fi
  fi

  info "Worker script OK"
  return 0
}

# Main health check
main() {
  local exit_code=0
  local checks_failed=0

  info "Starting health check..."
  info "State dir: ${STATE_DIR}"
  info "Worker script: ${WORKER_SCRIPT}"

  # Run all checks
  check_worker_script || ((checks_failed++))
  check_dependencies || exit_code=$?
  check_permissions || exit_code=$?
  check_lock_file || ((checks_failed++))
  check_heartbeat || ((checks_failed++))
  check_log_activity || ((checks_failed++))

  # Summary
  if [[ $exit_code -eq 2 ]]; then
    error "CRITICAL: Health check failed (manual intervention required)"
    error "Failed checks require manual resolution"
    return 2
  elif [[ $checks_failed -gt 0 ]]; then
    warn "UNHEALTHY: $checks_failed check(s) failed"

    if [[ ${#RECOVERY_ACTIONS[@]} -gt 0 ]]; then
      info "AUTO-RECOVERY actions taken: ${RECOVERY_ACTIONS[*]}"
    fi

    return 1
  else
    info "HEALTHY: All checks passed"
    return 0
  fi
}

# Run health check
main "$@"
