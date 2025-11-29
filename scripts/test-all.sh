#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mvn -f quarkus-app/pom.xml -T 1C -Dquarkus.devservices.enabled=false -DskipITs=false verify -Pcoverage
