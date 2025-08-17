#!/usr/bin/env bash
set -euo pipefail
cd quarkus-app
echo "== Enforcer =="
mvn -B -ntp -DskipTests enforcer:enforce@enforce
echo "== Analyze (no usadas/no declaradas) =="
mvn -B -ntp -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.6.1:analyze -DignoreNonCompile=true
echo "OK. Si hay warnings, corrige antes de abrir el PR."
