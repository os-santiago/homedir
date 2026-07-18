#!/usr/bin/env bash
# ============================================================================
# Migrate from systemd-native to Podman pod deployment
# ============================================================================
#
# Migrates existing HomeDir SDLC installation to containerized deployment
#
# Usage:
#   ./container/migrate-to-podman.sh [--dry-run]
#
# ============================================================================

set -euo pipefail

DRY_RUN="${1:-}"

log() {
  echo "[$(date +%Y-%m-%d\ %H:%M:%S)] $*"
}

run_cmd() {
  if [[ "$DRY_RUN" == "--dry-run" ]]; then
    echo "[DRY-RUN] Would run: $*"
  else
    log "Running: $*"
    "$@"
  fi
}

log "=========================================="
log "HomeDir SDLC - Migration to Podman Pod"
log "=========================================="

if [[ "$DRY_RUN" == "--dry-run" ]]; then
  log "DRY RUN MODE - No changes will be made"
fi

# ============================================================================
# Pre-flight Checks
# ============================================================================

log "Checking prerequisites..."

# Check podman installed
if ! command -v podman >/dev/null 2>&1; then
  log "ERROR: podman is not installed"
  log "Install with: sudo apt-get install -y podman"
  exit 1
fi

log "✓ Podman found: $(podman --version)"

# Check if systemd service exists
if systemctl --user list-unit-files | grep -q "homedir-sdlc-worker"; then
  HAS_USER_SERVICE=true
  SERVICE_TYPE="user"
elif systemctl list-unit-files 2>/dev/null | grep -q "homedir-sdlc-worker"; then
  HAS_USER_SERVICE=false
  SERVICE_TYPE="system"
else
  HAS_USER_SERVICE=""
  SERVICE_TYPE="none"
fi

if [[ -n "$HAS_USER_SERVICE" ]]; then
  log "✓ Found existing systemd service (type: $SERVICE_TYPE)"
else
  log "⚠ No existing systemd service found"
fi

# Check state directory
STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-/var/lib/homedir-sdlc}"
if [[ -d "$STATE_DIR" ]]; then
  log "✓ State directory exists: $STATE_DIR"

  # Count state files
  issue_count=$(find "$STATE_DIR/issues" -type f 2>/dev/null | wc -l || echo "0")
  pr_count=$(find "$STATE_DIR/prs" -type f 2>/dev/null | wc -l || echo "0")
  decision_count=$(find "$STATE_DIR/autonomous-decisions" -type f 2>/dev/null | wc -l || echo "0")

  log "  Issues: $issue_count"
  log "  PRs: $pr_count"
  log "  Autonomous decisions: $decision_count"
else
  log "⚠ State directory not found: $STATE_DIR"
  STATE_DIR=""
fi

# ============================================================================
# Step 1: Stop Existing Service
# ============================================================================

if [[ -n "$HAS_USER_SERVICE" ]]; then
  log ""
  log "Step 1: Stopping existing systemd service..."

  if [[ "$SERVICE_TYPE" == "user" ]]; then
    run_cmd systemctl --user stop homedir-sdlc-worker.service || true
    run_cmd systemctl --user stop homedir-sdlc-worker.timer || true
    log "✓ User service stopped"
  else
    run_cmd sudo systemctl stop homedir-sdlc-worker.service || true
    run_cmd sudo systemctl stop homedir-sdlc-worker.timer || true
    log "✓ System service stopped"
  fi
else
  log "Step 1: No existing service to stop"
fi

# ============================================================================
# Step 2: Backup Current State
# ============================================================================

log ""
log "Step 2: Backing up current state..."

BACKUP_DIR="${HOME}/homedir-sdlc-backup-$(date +%Y%m%d-%H%M%S)"

if [[ -n "$STATE_DIR" ]] && [[ -d "$STATE_DIR" ]]; then
  run_cmd mkdir -p "$BACKUP_DIR"
  run_cmd cp -r "$STATE_DIR" "$BACKUP_DIR/"
  log "✓ State backed up to: $BACKUP_DIR"
else
  log "⚠ No state to backup"
fi

# ============================================================================
# Step 3: Build Container Image
# ============================================================================

log ""
log "Step 3: Building container image..."

run_cmd podman build \
  -f container/Containerfile.sdlc-worker \
  -t localhost/homedir-sdlc:latest \
  .

log "✓ Image built"

# ============================================================================
# Step 4: Create Podman Volumes
# ============================================================================

log ""
log "Step 4: Creating Podman volumes..."

run_cmd podman volume create homedir-sdlc-state || log "Volume already exists"
run_cmd podman volume create homedir-sdlc-worktrees || log "Volume already exists"
run_cmd podman volume create homedir-sdlc-logs || log "Volume already exists"

log "✓ Volumes created"

# ============================================================================
# Step 5: Import State to Volume
# ============================================================================

if [[ -n "$STATE_DIR" ]] && [[ -d "$STATE_DIR" ]]; then
  log ""
  log "Step 5: Importing state to Podman volume..."

  # Create temporary container to import state
    if [[ "$DRY_RUN" != "--dry-run" ]]; then
    podman run --rm \
      -v "$STATE_DIR":/source:ro \
      -v homedir-sdlc-state:/dest \
      docker.io/library/alpine:latest \
      sh -c 'cp -r /source/* /dest/ && chown -R 1000:1000 /dest/ && ls -la /dest'

    log "✓ State imported to volume"
  else
    log "[DRY-RUN] Would import $STATE_DIR to volume"
  fi
else
  log "Step 5: No state to import"
fi

# ============================================================================
# Step 6: Create Environment Config
# ============================================================================

log ""
log "Step 6: Creating environment configuration..."

# Source base config; allow .local.env to override
if [[ -f "container/config/production.env" ]]; then
  set -a; source container/config/production.env; set +a
fi

if [[ -f "container/config/production.local.env" ]]; then
  set -a; source container/config/production.local.env; set +a
  log "✓ Loaded local overrides from production.local.env"
else
  run_cmd cp container/config/production.env container/config/production.local.env
  log "⚠ Created container/config/production.local.env — edit it to set GH_TOKEN"
  log "  nano container/config/production.local.env"
fi

# ============================================================================
# Step 7: Create Pod
# ============================================================================

log ""
log "Step 7: Creating Podman pod..."

if [[ "$DRY_RUN" != "--dry-run" ]]; then
  if [[ -f "container/config/production.local.env" ]]; then
    # Use local config if exists
    export $(grep -v '^#' container/config/production.local.env | xargs)
  fi

  ./container/pod-create.sh production
  log "✓ Pod created"
else
  log "[DRY-RUN] Would run: ./container/pod-create.sh production"
fi

# ============================================================================
# Step 8: Install SystemD Units
# ============================================================================

log ""
log "Step 8: Installing systemd units..."

if [[ "$SERVICE_TYPE" == "user" ]] || [[ "$SERVICE_TYPE" == "none" ]]; then
  # Install as user service
  run_cmd mkdir -p ~/.config/systemd/user/
  run_cmd cp container/systemd/*.service ~/.config/systemd/user/
  run_cmd cp container/systemd/*.timer ~/.config/systemd/user/
  run_cmd systemctl --user daemon-reload
  run_cmd systemctl --user enable homedir-sdlc-pod.service
  run_cmd systemctl --user enable homedir-sdlc-pod-autoupdate.timer

  log "✓ User systemd units installed"
else
  # Install as system service
  run_cmd sudo cp container/systemd/*.service /etc/systemd/system/
  run_cmd sudo cp container/systemd/*.timer /etc/systemd/system/
  run_cmd sudo systemctl daemon-reload
  run_cmd sudo systemctl enable homedir-sdlc-pod.service
  run_cmd sudo systemctl enable homedir-sdlc-pod-autoupdate.timer

  log "✓ System systemd units installed"
fi

# ============================================================================
# Step 9: Disable Old Service
# ============================================================================

if [[ -n "$HAS_USER_SERVICE" ]]; then
  log ""
  log "Step 9: Disabling old systemd service..."

  if [[ "$SERVICE_TYPE" == "user" ]]; then
    run_cmd systemctl --user disable homedir-sdlc-worker.service || true
    run_cmd systemctl --user disable homedir-sdlc-worker.timer || true
  else
    run_cmd sudo systemctl disable homedir-sdlc-worker.service || true
    run_cmd sudo systemctl disable homedir-sdlc-worker.timer || true
  fi

  log "✓ Old service disabled"
else
  log "Step 9: No old service to disable"
fi

# ============================================================================
# Summary
# ============================================================================

log ""
log "=========================================="
log "Migration Complete!"
log "=========================================="
log ""

if [[ "$DRY_RUN" == "--dry-run" ]]; then
  log "DRY RUN completed - no changes were made"
  log "Run without --dry-run to perform actual migration"
else
  log "✓ Container image built"
  log "✓ Podman volumes created"
  log "✓ State imported"
  log "✓ Pod created and running"
  log "✓ SystemD units installed"
  log ""
  log "Next steps:"
  log "  1. Edit container/config/production.local.env (set GH_TOKEN if not already)"
  log "  2. Verify pod is running: podman pod ps"
  log "  3. Check logs: podman logs -f homedir-sdlc-worker"
  log "  4. Access dashboard: http://localhost:8080/sdlc/dashboard"
  log ""
  log "Backup location: $BACKUP_DIR"
fi

log ""
