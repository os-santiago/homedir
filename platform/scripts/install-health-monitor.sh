#!/usr/bin/env bash
# Health Monitor Installation Script
#
# Usage: sudo -u homedir-sdlc ./install-health-monitor.sh

set -euo pipefail

USER="homedir-sdlc"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEMD_USER_DIR="/home/${USER}/.config/systemd/user"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

error() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] ERROR: $*" >&2
}

# Check if running as homedir-sdlc user
if [[ "$(whoami)" != "$USER" ]]; then
  error "This script must be run as $USER user"
  error "Run: sudo -u $USER $0"
  exit 1
fi

log "Installing health monitor..."

# Step 1: Make health check script executable
log "Making health check script executable..."
chmod +x "${SCRIPT_DIR}/worker-health-check.sh"

# Step 2: Create systemd user directory
log "Creating systemd user directory..."
mkdir -p "${SYSTEMD_USER_DIR}"

# Step 3: Copy service and timer files
log "Installing systemd service and timer..."
cp "${SCRIPT_DIR}/../systemd/worker-health-monitor.service" "${SYSTEMD_USER_DIR}/"
cp "${SCRIPT_DIR}/../systemd/worker-health-monitor.timer" "${SYSTEMD_USER_DIR}/"

# Step 4: Reload systemd
log "Reloading systemd..."
systemctl --user daemon-reload

# Step 5: Enable and start timer
log "Enabling and starting timer..."
systemctl --user enable worker-health-monitor.timer
systemctl --user start worker-health-monitor.timer

# Step 6: Test health check
log "Running initial health check..."
if "${SCRIPT_DIR}/worker-health-check.sh"; then
  log "✅ Health check PASSED"
else
  local exit_code=$?
  if [[ $exit_code -eq 1 ]]; then
    log "⚠️  Health check FAILED (auto-recovery attempted)"
  elif [[ $exit_code -eq 2 ]]; then
    error "Health check CRITICAL (manual intervention required)"
    exit 1
  fi
fi

# Step 7: Show status
log ""
log "================================================"
log "Health Monitor Installation Complete!"
log "================================================"
log ""
log "Timer Status:"
systemctl --user status worker-health-monitor.timer --no-pager | head -10
log ""
log "Next health check:"
systemctl --user list-timers worker-health-monitor.timer --no-pager
log ""
log "To view health check logs:"
log "  journalctl --user -u worker-health-monitor.service -f"
log ""
log "To manually run health check:"
log "  ${SCRIPT_DIR}/worker-health-check.sh"
log ""
