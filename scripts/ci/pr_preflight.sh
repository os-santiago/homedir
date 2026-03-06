#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}/quarkus-app"

echo "Running PR preflight: clean verify with coverage profile."
RETRY_ATTEMPTS="${RETRY_ATTEMPTS:-2}" RETRY_SLEEP_SECONDS="${RETRY_SLEEP_SECONDS:-20}" ../scripts/ci/retry.sh \
  ./mvnw clean verify -B -Pcoverage \
  -Dsurefire.rerunFailingTestsCount=1 \
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

echo "Running PR preflight: public smoke test."
RETRY_ATTEMPTS="${RETRY_ATTEMPTS:-2}" RETRY_SLEEP_SECONDS="${RETRY_SLEEP_SECONDS:-10}" ../scripts/ci/retry.sh \
  ./mvnw -B -Dtest=PublicExperienceSmokeTest test
