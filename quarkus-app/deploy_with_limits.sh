#!/bin/bash
# deploy_with_limits.sh
# Calculates 50% of Host RAM and deploys the container with limits.

# SECURITY: These values must be externalized to environment variables or GitHub Secrets
# Required environment variables:
#   DEPLOY_SSH_HOST - SSH host (e.g., root@72.60.141.165)
#   DEPLOY_SSH_KEY - Path to SSH private key
#
# WARNING: The previous hardcoded SSH key (id_ed25519_codex) has been exposed and must be rotated.
# Generate a new key and configure it in GitHub Secrets as DEPLOY_SSH_PRIVATE_KEY.

if [ -z "$DEPLOY_SSH_HOST" ]; then
  echo "ERROR: DEPLOY_SSH_HOST environment variable is not set" >&2
  exit 1
fi

if [ -z "$DEPLOY_SSH_KEY" ]; then
  echo "ERROR: DEPLOY_SSH_KEY environment variable is not set" >&2
  exit 1
fi

HOST="$DEPLOY_SSH_HOST"
KEY="$DEPLOY_SSH_KEY"

echo "--- Detecting Host Resources ---"
MEM_KB=$(ssh -i "$KEY" -o StrictHostKeyChecking=no $HOST "grep MemTotal /proc/meminfo" | awk '{print $2}')
echo "Total Memory: $MEM_KB kB"

# Calculate 50%
LIMIT_KB=$((MEM_KB / 2))
LIMIT_MB=$((LIMIT_KB / 1024))
echo "Memory Limit (50%): $LIMIT_MB MB"

# Convert to G for display
LIMIT_GB=$(echo "scale=2; $LIMIT_MB/1024" | bc)
echo "Setting Limit: ${LIMIT_GB}GB"

# Deployment Command (Example)
# podman run -d --name homedir-app --memory ${LIMIT_MB}m -p 8080:8080 quay.io/os-santiago/homedir:latest
echo "--- Deployment Command ---"
echo "podman run -d --name homedir-app --memory ${LIMIT_MB}m -p 8080:8080 quay.io/os-santiago/homedir:latest"
