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
# Run in a new process group (when setsid is available, e.g. Linux CI) so we
# can kill the forked JVM that quarkus:dev spawns, not just the mvnw wrapper.
if command -v setsid >/dev/null 2>&1; then
  setsid ./mvnw quarkus:dev -Ddebug=false -Dquarkus.http.port="${E2E_PORT}" "${MAVEN_ARGS[@]}" > quarkus-dev.log 2>&1 &
else
  ./mvnw quarkus:dev -Ddebug=false -Dquarkus.http.port="${E2E_PORT}" "${MAVEN_ARGS[@]}" > quarkus-dev.log 2>&1 &
fi
QUARKUS_PID=$!
cd ..

cleanup() {
  if [ -n "${QUARKUS_PID:-}" ]; then
    echo "🛑 Stopping Quarkus (PID: $QUARKUS_PID)..."
    # Kill the whole process group (wrapper + forked JVM) so the dev server
    # does not linger and hold the port after the script exits.
    kill -- "-${QUARKUS_PID}" 2>/dev/null || true
    pkill -P "${QUARKUS_PID}" 2>/dev/null || true
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
set +e
E2E_BASE_URL="${E2E_BASE_URL}" npm --prefix tests/e2e run test:e2e
test_exit=$?
set -e
if [ "$test_exit" -ne 0 ]; then
  echo "❌ Playwright E2E tests failed (exit code $test_exit). Dumping Quarkus dev log..."
  tail -n 100 quarkus-app/quarkus-dev.log
fi
exit "$test_exit"
