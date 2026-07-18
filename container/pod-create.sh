#!/usr/bin/env bash
# ============================================================================
# Create HomeDir SDLC Pod with Podman
# ============================================================================
#
# Creates a Podman pod with both homedir-app and homedir-sdlc-worker containers
# Containers share network namespace (can communicate via localhost)
#
# Usage:
#   ./container/pod-create.sh [environment]
#
# Environments: development, staging, production (default: production)
#
# ============================================================================

set -euo pipefail

ENVIRONMENT="${1:-production}"
POD_NAME="homedir-sdlc-pod"

echo "Creating HomeDir SDLC Pod for environment: ${ENVIRONMENT}"

# ============================================================================
# Configuration
# ============================================================================

# Load environment-specific config
CONFIG_DIR="$(dirname "$0")/config"
ENV_FILE="${CONFIG_DIR}/${ENVIRONMENT}.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE"
  exit 1
fi

echo "Loading configuration from: $ENV_FILE"
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

# ============================================================================
# Validate Required Variables
# ============================================================================

REQUIRED_VARS=(
  "GH_TOKEN"
  "HOMEDIR_IMAGE"
  "HOMEDIR_SDLC_IMAGE"
  "HOMEDIR_SDLC_REPO"
)

for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: Required variable $var not set in $ENV_FILE"
    exit 1
  fi
done

# ============================================================================
# Stop and Remove Existing Pod
# ============================================================================

if podman pod exists "$POD_NAME" 2>/dev/null; then
  echo "Stopping existing pod: $POD_NAME"
  podman pod stop "$POD_NAME" 2>/dev/null || true

  echo "Removing existing pod: $POD_NAME"
  podman pod rm "$POD_NAME" 2>/dev/null || true
fi

# ============================================================================
# Create Volumes
# ============================================================================

echo "Creating/verifying volumes..."

# State volume (shared between worker and app)
podman volume create homedir-sdlc-state 2>/dev/null || echo "Volume homedir-sdlc-state already exists"

# Worktrees volume (worker only)
podman volume create homedir-sdlc-worktrees 2>/dev/null || echo "Volume homedir-sdlc-worktrees already exists"

# Logs volume
podman volume create homedir-sdlc-logs 2>/dev/null || echo "Volume homedir-sdlc-logs already exists"

echo "Volumes ready"

# ============================================================================
# Create Pod
# ============================================================================

echo "Creating pod: $POD_NAME"

podman pod create \
  --name "$POD_NAME" \
  --publish 8080:8080 \
  --network bridge \
  --label "app=homedir-sdlc" \
  --label "environment=${ENVIRONMENT}"

echo "Pod created successfully"

# ============================================================================
# Start homedir-app Container
# ============================================================================

echo "Creating homedir-app container..."

podman run -d \
  --name homedir-app \
  --pod "$POD_NAME" \
  --restart unless-stopped \
  --volume homedir-sdlc-state:/var/lib/homedir-sdlc:ro,z \
  --env QUARKUS_PROFILE="${ENVIRONMENT}" \
  --label "app=homedir-app" \
  --label "component=frontend" \
  "${HOMEDIR_IMAGE}"

echo "homedir-app container started"

# ============================================================================
# Start homedir-sdlc-worker Container
# ============================================================================

echo "Creating homedir-sdlc-worker container..."

# Mount policy config
POLICY_CONFIG_PATH="${PWD}/platform/config/autonomous-decision-policy.yaml"

if [[ ! -f "$POLICY_CONFIG_PATH" ]]; then
  echo "WARN: Policy file not found at $POLICY_CONFIG_PATH"
  echo "WARN: Worker will run without policies"
  POLICY_MOUNT=""
else
  echo "Mounting policy config from: $POLICY_CONFIG_PATH"
  POLICY_MOUNT="--volume ${POLICY_CONFIG_PATH}:/app/config/autonomous-decision-policy.yaml:ro,z"
fi

podman run -d \
  --name homedir-sdlc-worker \
  --pod "$POD_NAME" \
  --restart unless-stopped \
  --volume homedir-sdlc-state:/var/lib/homedir-sdlc:z \
  --volume homedir-sdlc-worktrees:/srv/homedir-sdlc/worktrees:z \
  --volume homedir-sdlc-logs:/var/log/homedir-sdlc:z \
  ${POLICY_MOUNT} \
  --env GH_TOKEN="${GH_TOKEN}" \
  --env HOMEDIR_SDLC_REPO="${HOMEDIR_SDLC_REPO}" \
  --env HOMEDIR_SDLC_STATE_DIR=/var/lib/homedir-sdlc \
  --env HOMEDIR_SDLC_WORKDIR=/srv/homedir-sdlc/worktrees/homedir \
  --env HOMEDIR_SDLC_LOGFILE=/var/log/homedir-sdlc/worker.log \
  --env PLATFORM_DIR=/app \
  --env SCC_PROFILE="${SCC_PROFILE:-nvidia}" \
  --env SCC_BIN=/usr/local/bin/scc \
  --label "app=homedir-sdlc-worker" \
  --label "component=worker" \
  "${HOMEDIR_SDLC_IMAGE}"

echo "homedir-sdlc-worker container started"

# ============================================================================
# Pod Status
# ============================================================================

echo ""
echo "=========================================="
echo "Pod created successfully!"
echo "=========================================="
echo ""

podman pod ps --filter "name=${POD_NAME}"

echo ""
echo "Containers in pod:"
podman ps --filter "pod=${POD_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "Useful commands:"
echo "  podman pod logs -f ${POD_NAME}              # Follow all logs"
echo "  podman logs -f homedir-sdlc-worker          # Follow worker logs"
echo "  podman exec -it homedir-sdlc-worker bash    # Shell into worker"
echo "  podman pod stop ${POD_NAME}                 # Stop pod"
echo "  podman pod start ${POD_NAME}                # Start pod"
echo "  podman pod rm -f ${POD_NAME}                # Remove pod"
echo ""
echo "Dashboard: http://localhost:8080/sdlc/dashboard"
echo ""
