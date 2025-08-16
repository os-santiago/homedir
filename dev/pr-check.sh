#!/usr/bin/env bash
set -euo pipefail
cd quarkus-app
mvn -B -ntp -DskipITs=false verify -Pcoverage
REPORT=$(git ls-files -z | tr '\0' '\n' | grep -E 'target/site/jacoco/index\.html$' | head -n1 || true)
echo
echo "✅ Build + tests OK. Abre el reporte de cobertura local:"
echo "   ${REPORT:-<no encontrado>}"
echo "Recuerda que el gate en CI evaluará SOLO el diff del PR (líneas y branches)."
