#!/usr/bin/env bash
set -euo pipefail

echo "== Estilo (local quick) =="
# agrega aquí tu verificador local (ej., Spotless: mvn spotless:apply && mvn spotless:check)
echo "(placeholder) style OK"

echo "== Tests & Cobertura (local) =="
cd quarkus-app
mvn -B -ntp -DskipITs=false verify -Pcoverage
rep=$(git ls-files -z | tr '\0' '\n' | grep -E 'target/site/jacoco/index\.html$' | head -n1 || true)
echo "Abre el reporte de cobertura: ${rep:-<no encontrado>}"
