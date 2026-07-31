#!/usr/bin/env bash
# Run Playwright E2E tests against a locally started Quarkus (dev profile).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}"

E2E_PORT="${E2E_PORT:-8080}"
E2E_BASE_URL="http://localhost:${E2E_PORT}"
MAVEN_ARGS=()
if [ -n "${MAVEN_REPO_LOCAL:-}" ]; then
  MAVEN_ARGS+=("-Dmaven.repo.local=${MAVEN_REPO_LOCAL}")
fi

echo "🚀 Starting Quarkus (dev profile) for E2E tests..."
cd quarkus-app
./mvnw quarkus:dev -Ddebug=false -Dquarkus.http.port="${E2E_PORT}" "${MAVEN_ARGS[@]}" > quarkus-dev.log 2>&1 &
QUARKUS_PID=$!
cd ..

cleanup() {
  if [ -n "${QUARKUS_PID:-}" ]; then
    echo "🛑 Stopping Quarkus (PID: $QUARKUS_PID)..."
    kill "$QUARKUS_PID" 2>/dev/null || true
    wait "$QUARKUS_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "⏳ Waiting for Quarkus..."
max_wait=90
elapsed=0
while ! curl -sf "${E2E_BASE_URL}/q/health/ready" > /dev/null 2>&1; do
  if [ "$elapsed" -ge "$max_wait" ]; then
    echo "❌ Quarkus failed to start"
    cat quarkus-app/quarkus-dev.log
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done
echo "✅ Quarkus started at ${E2E_BASE_URL}"

echo "📦 Installing Playwright dependencies..."
cd tests/e2e
npm ci
if [ -n "${CI:-}" ]; then
  npx playwright install --with-deps chromium
else
  npx playwright install chromium
fi
cd ../..

echo "🧪 Running Playwright E2E tests..."
E2E_BASE_URL="${E2E_BASE_URL}" npm --prefix tests/e2e run test:e2e
