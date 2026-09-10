#!/bin/bash
# ============================================================================
# Deploy AI-SDLC Worker with SCC to K3s
# ============================================================================
#
# This script deploys the updated worker container with SCC support to K3s
#
# Prerequisites:
# - Worker image built: ghcr.io/os-santiago/homedir-ai-sdlc-worker:latest
# - WSL configured with SSH access to VPS
# - K3s cluster running on VPS
#
# ============================================================================

set -euo pipefail

# Configuration
IMAGE_NAME="ghcr.io/os-santiago/homedir-ai-sdlc-worker:latest"
TAR_FILE="/tmp/worker-with-scc.tar"
VPS_HOST="root@72.60.141.165"
SSH_KEY="/home/scanales/.ssh/id_ed25519"
NAMESPACE="homedir-ai-sdlc"

log() {
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*" >&2
}

# ============================================================================
# Step 1: Export Image
# ============================================================================
log "INFO: Exporting image ${IMAGE_NAME}..."

if ! podman save "${IMAGE_NAME}" -o "${TAR_FILE}"; then
  log "ERROR: Failed to export image"
  exit 1
fi

IMAGE_SIZE=$(du -h "${TAR_FILE}" | cut -f1)
log "INFO: Image exported: ${TAR_FILE} (${IMAGE_SIZE})"

# ============================================================================
# Step 2: Upload to VPS (via WSL)
# ============================================================================
log "INFO: Uploading image to VPS..."

if ! wsl scp -i "${SSH_KEY}" "${TAR_FILE}" "${VPS_HOST}:/tmp/"; then
  log "ERROR: Failed to upload image to VPS"
  exit 1
fi

log "INFO: Image uploaded to VPS:/tmp/$(basename ${TAR_FILE})"

# ============================================================================
# Step 3: Import to K3s
# ============================================================================
log "INFO: Importing image to K3s..."

if ! wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
  "k3s ctr images import /tmp/$(basename ${TAR_FILE})"; then
  log "ERROR: Failed to import image to K3s"
  exit 1
fi

log "INFO: Image imported to K3s"

# ============================================================================
# Step 4: Verify Image
# ============================================================================
log "INFO: Verifying image in K3s..."

IMAGE_LIST=$(wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
  "k3s ctr images ls | grep homedir-ai-sdlc-worker")

log "INFO: K3s images:"
log "${IMAGE_LIST}"

# ============================================================================
# Step 5: Delete Old Jobs
# ============================================================================
log "INFO: Deleting old jobs to force new pod creation..."

wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
  "k3s kubectl delete job --all -n ${NAMESPACE}" || true

log "INFO: Old jobs deleted"

# ============================================================================
# Step 6: Create Verification Job
# ============================================================================
log "INFO: Creating verification job..."

JOB_NAME="scc-verification-$(date +%Y%m%d-%H%M%S)"

wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
  "k3s kubectl create job --from=cronjob/ai-sdlc-worker ${JOB_NAME} -n ${NAMESPACE}"

log "INFO: Verification job created: ${JOB_NAME}"

# ============================================================================
# Step 7: Wait for Job to Start
# ============================================================================
log "INFO: Waiting for job to start (max 60s)..."

for i in {1..12}; do
  POD_NAME=$(wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
    "k3s kubectl get pods -n ${NAMESPACE} -l job-name=${JOB_NAME} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null" || echo "")

  if [[ -n "${POD_NAME}" ]]; then
    log "INFO: Pod started: ${POD_NAME}"
    break
  fi

  log "INFO: Waiting for pod... (${i}/12)"
  sleep 5
done

if [[ -z "${POD_NAME}" ]]; then
  log "WARN: Pod did not start within 60s"
  log "WARN: Check manually with: kubectl get pods -n ${NAMESPACE}"
  exit 1
fi

# ============================================================================
# Step 8: Monitor Logs
# ============================================================================
log "INFO: Monitoring logs for SCC verification..."
log "INFO: Looking for:"
log "  - 'SCC found: sc-agent-cli'"
log "  - 'SCC configured with profile: nvidia'"
log "  - 'SCC available at /usr/local/bin/scc'"
log ""
log "=========================================="

wsl ssh -i "${SSH_KEY}" "${VPS_HOST}" \
  "k3s kubectl logs -n ${NAMESPACE} -l job-name=${JOB_NAME} --tail=100 -f"

log ""
log "=========================================="
log "INFO: Deployment complete!"
log ""
log "Next steps:"
log "  1. Verify SCC is working in logs above"
log "  2. Check for 'SCC found' and 'SCC configured' messages"
log "  3. Execute E2E test with oldest existing issue"
log "  4. Monitor worker for 24-48h to validate autonomy"
log ""
log "Commands:"
log "  # Get pod logs"
log "  wsl ssh -i ${SSH_KEY} ${VPS_HOST} 'k3s kubectl logs -n ${NAMESPACE} -l job-name=${JOB_NAME}'"
log ""
log "  # Exec into pod"
log "  wsl ssh -i ${SSH_KEY} ${VPS_HOST} 'k3s kubectl exec -n ${NAMESPACE} ${POD_NAME} -it -- bash'"
log ""
log "  # Test SCC manually"
log "  wsl ssh -i ${SSH_KEY} ${VPS_HOST} 'k3s kubectl exec -n ${NAMESPACE} ${POD_NAME} -- scc chat -m nvidia -yq \"Say OK\"'"
