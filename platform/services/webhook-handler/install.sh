#!/usr/bin/env bash
# Webhook Handler Installation Script
#
# Usage: sudo ./install.sh [--dry-run]

set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "DRY RUN MODE - No changes will be made"
fi

SERVICE_DIR="/home/homedir-sdlc/platform/services/webhook-handler"
CONFIG_DIR="/home/homedir-sdlc/.config/homedir-sdlc"
SYSTEMD_SERVICE="/etc/systemd/system/webhook-handler.service"
NODE_BIN="/home/homedir-sdlc/.local/opt/node-v24.16.0-linux-x64/bin/node"
NPM_BIN="/home/homedir-sdlc/.local/opt/node-v24.16.0-linux-x64/bin/npm"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

error() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] ERROR: $*" >&2
}

# Check if running as root
if [[ $EUID -ne 0 ]]; then
  error "This script must be run as root (use sudo)"
  exit 1
fi

# Check if service directory exists
if [[ ! -d "$SERVICE_DIR" ]]; then
  error "Service directory not found: $SERVICE_DIR"
  exit 1
fi

# Check if Node.js is installed
if [[ ! -x "$NODE_BIN" ]]; then
  error "Node.js not found at $NODE_BIN"
  exit 1
fi

log "Installing webhook handler..."

# Step 1: Install dependencies
log "Installing npm dependencies..."
if [[ "$DRY_RUN" == "false" ]]; then
  cd "$SERVICE_DIR"
  sudo -u homedir-sdlc "$NPM_BIN" install --production
else
  log "[DRY RUN] Would run: npm install --production"
fi

# Step 2: Create environment file if it doesn't exist
ENV_FILE="$CONFIG_DIR/webhook.env"
if [[ ! -f "$ENV_FILE" ]]; then
  log "Creating environment file: $ENV_FILE"

  if [[ "$DRY_RUN" == "false" ]]; then
    # Generate random webhook secret
    WEBHOOK_SECRET=$(openssl rand -hex 32)

    cat > "$ENV_FILE" << EOF
# GitHub Webhook Handler Configuration
# Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')

# GitHub webhook secret (keep this secret!)
WEBHOOK_SECRET=$WEBHOOK_SECRET

# Repository to accept webhooks from
ALLOWED_REPO=os-santiago/homedir

# Port to listen on
PORT=3000

# Worker script path
WORKER_SCRIPT=/home/homedir-sdlc/.local/bin/homedir-sdlc-worker.sh
EOF

    chown homedir-sdlc:homedir-sdlc "$ENV_FILE"
    chmod 600 "$ENV_FILE"

    log "Webhook secret generated: $WEBHOOK_SECRET"
    log "⚠️  SAVE THIS SECRET - You'll need it for GitHub webhook configuration"
  else
    log "[DRY RUN] Would create $ENV_FILE with generated webhook secret"
  fi
else
  log "Environment file already exists: $ENV_FILE"
fi

# Step 3: Install systemd service
log "Installing systemd service..."
if [[ "$DRY_RUN" == "false" ]]; then
  cp "$(dirname "$0")/../../systemd/webhook-handler.service" "$SYSTEMD_SERVICE"
  systemctl daemon-reload
else
  log "[DRY RUN] Would copy service file to $SYSTEMD_SERVICE"
fi

# Step 4: Enable and start service
log "Enabling and starting service..."
if [[ "$DRY_RUN" == "false" ]]; then
  systemctl enable webhook-handler.service
  systemctl start webhook-handler.service

  # Wait for service to start
  sleep 2

  # Check status
  if systemctl is-active --quiet webhook-handler.service; then
    log "✅ Service started successfully"
  else
    error "Service failed to start"
    systemctl status webhook-handler.service --no-pager
    exit 1
  fi
else
  log "[DRY RUN] Would enable and start webhook-handler.service"
fi

# Step 5: Test health endpoint
log "Testing health endpoint..."
if [[ "$DRY_RUN" == "false" ]]; then
  if curl -s -f http://localhost:3000/health > /dev/null; then
    log "✅ Health check passed"
  else
    error "Health check failed"
    exit 1
  fi
else
  log "[DRY RUN] Would test http://localhost:3000/health"
fi

# Step 6: Show configuration summary
log ""
log "================================================"
log "Webhook Handler Installation Complete!"
log "================================================"
log ""
log "Service Status:"
if [[ "$DRY_RUN" == "false" ]]; then
  systemctl status webhook-handler.service --no-pager | head -10
else
  log "[DRY RUN] Service status would be shown here"
fi
log ""
log "Next Steps:"
log "1. Configure GitHub webhook:"
log "   - URL: http://YOUR-VPS-IP:3000/webhook/github"
log "   - Content type: application/json"

if [[ -f "$ENV_FILE" && "$DRY_RUN" == "false" ]]; then
  SECRET=$(grep WEBHOOK_SECRET "$ENV_FILE" | cut -d= -f2)
  log "   - Secret: $SECRET"
fi

log "   - Events: Issues, Pull requests, Check suites, Issue comments, PR reviews"
log ""
log "2. Configure firewall (if needed):"
log "   sudo ufw allow 3000/tcp"
log ""
log "3. Monitor logs:"
log "   journalctl -u webhook-handler.service -f"
log ""
log "4. Test webhook:"
log "   curl http://localhost:3000/health"
log ""
