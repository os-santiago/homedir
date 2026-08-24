#!/usr/bin/env bash
# ============================================================================
# HomeDir AI SDLC Worker - Container Entrypoint
# ============================================================================
#
# Initializes the worker environment and starts the SDLC reconciliation loop
#
# ============================================================================

set -euo pipefail

# Logging
log() {
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] [entrypoint] $*" >&2
}

log "INFO: HomeDir AI SDLC Worker starting..."

# Handle --help and --version before auth
if [[ "${1:-}" == "--help" ]] || [[ "${1:-}" == "-h" ]]; then
  echo "Usage: worker-entrypoint.sh [command]"
  echo ""
  echo "Commands:"
  echo "  reconcile    Run the SDLC reconciliation loop (default)"
  echo "  --help       Show this help message"
  echo "  --version    Show version information"
  echo ""
  echo "Environment variables:"
  echo "  GH_TOKEN                  GitHub token (required)"
  echo "  HOMEDIR_SDLC_REPO         Repository (default: os-santiago/homedir)"
  echo "  HOMEDIR_SDLC_STATE_DIR    State directory (default: /var/lib/homedir-sdlc)"
  echo "  HOMEDIR_SDLC_WORKDIR      Worktree directory"
  echo "  PLATFORM_DIR              Platform scripts directory (default: /app)"
  exit 0
fi

if [[ "${1:-}" == "--version" ]]; then
  echo "homedir-sdlc-worker 1.0.0"
  exit 0
fi

# ============================================================================
# Environment Validation
# ============================================================================
log "INFO: Validating environment..."

# Required environment variables
REQUIRED_VARS=(
  "GH_TOKEN"
  "HOMEDIR_SDLC_REPO"
  "HOMEDIR_SDLC_STATE_DIR"
  "HOMEDIR_SDLC_WORKDIR"
  "PLATFORM_DIR"
)

for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    log "ERROR: Required environment variable $var is not set"
    exit 1
  fi
done

log "INFO: Environment validation passed"

# ============================================================================
# GitHub CLI Authentication
# ============================================================================
log "INFO: Configuring GitHub CLI..."

# Authenticate with token (required for gh auth status to pass)
if [[ -n "${GH_TOKEN:-}" ]]; then
  log "INFO: Authenticating with GH_TOKEN..."

  # Write token to gh auth login stdin (non-interactive)
  if echo "${GH_TOKEN}" | gh auth login --with-token >/dev/null 2>&1; then
    log "INFO: GitHub CLI authenticated via token"
  else
    log "WARN: gh auth login failed, but gh CLI will still use GH_TOKEN env var"
  fi

  # Verify authentication (informational only, not fatal)
  if gh auth status >/dev/null 2>&1; then
    log "INFO: GitHub CLI authentication verified"
  else
    log "WARN: gh auth status check failed, but operations will use GH_TOKEN directly"
  fi
else
  log "ERROR: GH_TOKEN environment variable is not set"
  exit 1
fi

# ============================================================================
# Git Configuration
# ============================================================================
log "INFO: Configuring Git..."

git config --global user.name "${HOMEDIR_SDLC_GIT_USER_NAME:-homedir-sdlc[bot]}"
git config --global user.email "${HOMEDIR_SDLC_GIT_USER_EMAIL:-homedir-sdlc@users.noreply.github.com}"
git config --global init.defaultBranch main
git config --global pull.rebase false

log "INFO: Git configured"

# ============================================================================
# Policy System Check
# ============================================================================
log "INFO: Checking policy system..."

POLICY_FILE="${PLATFORM_DIR}/config/autonomous-decision-policy.yaml"

if [[ -f "$POLICY_FILE" ]]; then
  log "INFO: Policy file found: $POLICY_FILE"

  # Count policies (rough estimate)
  policy_count=$(grep -c "^  [a-z_].*:" "$POLICY_FILE" || echo "0")
  log "INFO: Estimated $policy_count policy entries loaded"
else
  log "WARN: Policy file not found at $POLICY_FILE"
  log "WARN: Worker will run with autonomous guidelines only (no policies)"
fi

# ============================================================================
# State Directory Setup
# ============================================================================
log "INFO: Initializing state directories..."

mkdir -p "${HOMEDIR_SDLC_STATE_DIR}"/{issues,prs,run-summaries,autonomous-decisions}
mkdir -p "$(dirname "${HOMEDIR_SDLC_LOGFILE}")"
mkdir -p "${HOMEDIR_SDLC_WORKDIR}"

log "INFO: State directories ready"

# ============================================================================
# Clone/Update Repository Worktree
# ============================================================================
log "INFO: Setting up repository worktree..."

REPO_NAME=$(basename "$HOMEDIR_SDLC_REPO")
WORKTREE_PATH="${HOMEDIR_SDLC_WORKDIR}"

if [[ ! -d "${WORKTREE_PATH}/.git" ]]; then
  log "INFO: Cloning repository ${HOMEDIR_SDLC_REPO}..."

  # Clone repository
  if ! gh repo clone "${HOMEDIR_SDLC_REPO}" "${WORKTREE_PATH}"; then
    log "ERROR: Failed to clone repository"
    exit 1
  fi

  log "INFO: Repository cloned successfully"
else
  log "INFO: Repository already exists, updating..."

  # Update repository
  cd "${WORKTREE_PATH}"
  git fetch origin >/dev/null 2>&1 || log "WARN: Git fetch failed"

  log "INFO: Repository updated"
fi

log "INFO: Worktree ready at ${WORKTREE_PATH}"

# ============================================================================
# SCC Configuration and Verification
# ============================================================================
log "INFO: Configuring SCC (sc-agent-cli)..."

# Create SCC config directory
mkdir -p ~/.sc-agent

# Create config from template, substituting NVIDIA_API_KEY
if [[ -f /app/config/sc-agent-config.json ]]; then
  if [[ -n "${NVIDIA_API_KEY:-}" ]]; then
    # Replace ${NVIDIA_API_KEY} placeholder with actual value
    sed "s/\${NVIDIA_API_KEY}/${NVIDIA_API_KEY}/g" /app/config/sc-agent-config.json > ~/.sc-agent/config.json
    log "INFO: SCC config created with NVIDIA API key"
  else
    log "WARN: NVIDIA_API_KEY not set - SCC will not function"
    cp /app/config/sc-agent-config.json ~/.sc-agent/config.json
  fi
else
  log "WARN: SCC config template not found"
fi

# Verify SCC installation
if command -v "${SCC_BIN:-scc}" >/dev/null 2>&1; then
  SCC_VERSION=$("${SCC_BIN:-scc}" --version 2>&1 | head -1 || echo "unknown")
  log "INFO: SCC found: ${SCC_VERSION}"

  # Test SCC can connect to API
  if [[ -n "${NVIDIA_API_KEY:-}" ]]; then
    log "INFO: SCC configured with profile: ${SCC_PROFILE:-nvidia}"
  else
    log "WARN: NVIDIA_API_KEY not set - SCC cannot call AI models"
  fi
else
  log "WARN: SCC not found at ${SCC_BIN:-scc}"
  log "WARN: Worker will operate in reconcile-only mode"
fi

# ============================================================================
# Write Initial Heartbeat
# ============================================================================
log "INFO: Writing initial heartbeat..."

cat > "${HOMEDIR_SDLC_STATE_DIR}/heartbeat.json" <<EOF
{
  "repo": "${HOMEDIR_SDLC_REPO}",
  "status": "starting",
  "detail": "Container initialized, starting worker",
  "updated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "container": true,
  "platform_dir": "${PLATFORM_DIR}"
}
EOF

log "INFO: Heartbeat written"

# ============================================================================
# Start Worker
# ============================================================================
log "INFO: Starting AI SDLC worker..."
log "INFO: Mode: ${1:-reconcile}"
log "INFO: Repository: ${HOMEDIR_SDLC_REPO}"
log "INFO: State: ${HOMEDIR_SDLC_STATE_DIR}"
log "INFO: Worktree: ${HOMEDIR_SDLC_WORKDIR}"

# Execute worker script
exec "${PLATFORM_DIR}/scripts/homedir-sdlc-worker.sh" "$@"
