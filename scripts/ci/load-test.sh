#!/usr/bin/env bash
# Lightweight load testing for high-impact PRs
set -euo pipefail

HIGH_IMPACT_SERVICES=(
  "quarkus-app/src/main/java/com/scanales/homedir/service/PersistenceService.java"
  "quarkus-app/src/main/java/com/scanales/homedir/trending/TrendingService.java"
  "quarkus-app/src/main/java/com/scanales/homedir/community/CommunityContentService.java"
  "quarkus-app/src/main/java/com/scanales/homedir/cfp/CfpSubmissionService.java"
)

BASELINE_DIR=".github/baselines"
BASELINE_FILE="${BASELINE_DIR}/load-test-baseline.json"

echo "🔍 Checking if PR touches high-impact services..."
changed_files=$(git diff --name-only origin/main...HEAD || true)
high_impact_changed=false

for service in "${HIGH_IMPACT_SERVICES[@]}"; do
  if echo "$changed_files" | grep -q "$(basename "$service")"; then
    echo "✅ High-impact service modified: $(basename "$service")"
    high_impact_changed=true
  fi
done

if [ "$high_impact_changed" = false ]; then
  echo "ℹ️  No high-impact services modified. Skipping load test."
  exit 0
fi

echo "🚀 Starting Quarkus dev mode for load testing..."
cd quarkus-app
./mvnw quarkus:dev -Ddebug=false > quarkus-dev.log 2>&1 &
QUARKUS_PID=$!

cleanup() {
  if [ -n "${QUARKUS_PID:-}" ]; then
    echo "🛑 Stopping Quarkus (PID: $QUARKUS_PID)..."
    kill $QUARKUS_PID || true
    wait $QUARKUS_PID 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "⏳ Waiting for Quarkus..."
max_wait=60
elapsed=0
while ! curl -sf http://localhost:8080/q/health/ready > /dev/null 2>&1; do
  if [ $elapsed -ge $max_wait ]; then
    echo "❌ Quarkus failed to start"
    cat quarkus-dev.log
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

echo "✅ Quarkus started"
cd ..

echo "📊 Running k6 load test..."
k6 run --out json=load-test-results.json --summary-export=load-test-summary.json scripts/ci/load-test.js

p95_latency=$(cat load-test-summary.json | grep -oP '"p\(95\)":{\s*"value":\K[0-9.]+' | head -1 || echo "0")
echo "P95 Latency: ${p95_latency}ms"

if [ -f "$BASELINE_FILE" ]; then
  baseline_p95=$(cat "$BASELINE_FILE" | grep -oP '"p95_latency":\K[0-9.]+')
  threshold=$(awk "BEGIN {print $baseline_p95 * 1.5}")
  echo "Baseline: ${baseline_p95}ms, Threshold: ${threshold}ms"
  
  if awk "BEGIN {exit !($p95_latency > $threshold)}"; then
    echo "❌ PERFORMANCE REGRESSION: ${p95_latency}ms > ${threshold}ms"
    exit 1
  fi
  echo "✅ Performance within range"
else
  echo "ℹ️  No baseline. Informational mode."
fi

[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat >> "$GITHUB_STEP_SUMMARY" << EOF
## Load Test Results
- **P95 Latency**: ${p95_latency}ms
- **Baseline**: ${baseline_p95:-N/A}ms
EOF

echo "✅ Load test complete"
