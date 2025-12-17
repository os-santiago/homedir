#!/bin/bash
# deploy_with_limits.sh
# Calculates 50% of Host RAM and deploys the container with limits.

HOST="root@72.60.141.165"
KEY="C:\Users\sergi\.ssh\id_ed25519_codex"
# Note: In a real bash script on Windows we need to be careful with paths.
# If running fron Git Bash, paths are different. Assuming this runs in a context where ssh works.

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
