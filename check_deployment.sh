#!/bin/bash
# Script to check deployment status and configure admin token

echo "Checking deployment status..."
echo "============================================================"

# Check if server is reachable
ssh -i ~/.ssh/id_ed25519 root@72.60.141.165 << 'EOF'
echo "1. Checking container status..."
podman ps | grep homedir

echo ""
echo "2. Checking homedir version..."
podman exec homedir cat /deployments/quarkus-app/quarkus-run.jar 2>/dev/null | head -c 20 || echo "Could not determine version"

echo ""
echo "3. Checking if LOCALHOST_ADMIN_TOKEN is configured..."
if podman exec homedir env | grep -q LOCALHOST_ADMIN_TOKEN; then
    echo "✓ LOCALHOST_ADMIN_TOKEN is configured"
else
    echo "✗ LOCALHOST_ADMIN_TOKEN is NOT configured"
    echo ""
    echo "To configure:"
    echo "  export LOCALHOST_ADMIN_TOKEN=\$(openssl rand -hex 32)"
    echo "  echo \$LOCALHOST_ADMIN_TOKEN > /tmp/admin_token.txt"
    echo "  podman restart homedir"
fi

echo ""
echo "4. Checking API health..."
podman exec homedir curl -s http://localhost:8080/q/health | head -20

echo ""
echo "5. Checking if localhost admin API is available..."
if podman exec homedir curl -s http://localhost:8080/api/localhost-admin/status 2>&1 | grep -q "localhost_only\|missing_token"; then
    echo "✓ Localhost admin API is available"
else
    echo "✗ Localhost admin API NOT available (may need restart)"
fi

echo ""
echo "============================================================"
echo "Deployment check complete"
EOF
