#!/usr/bin/env bash
# Run Maven/Quarkus inside a disposable container, so contributors do not need
# JDK 21 or Maven installed on the host.
#
#   scripts/dev-container.sh                 # quarkus:dev with live reload on :8080
#   scripts/dev-container.sh test            # run the test suite
#   scripts/dev-container.sh clean package   # any other Maven goal
#
# The dependency cache lives in .tmp/m2 (git-ignored) instead of ~/.m2, so the
# workflow leaves nothing outside the repository. To remove every trace:
#
#   rm -rf .tmp/m2 .tmp/maven-home quarkus-app/target
#   docker rmi maven:3.9-eclipse-temurin-21
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
M2_DIR="$REPO_ROOT/.tmp/m2"
# Container HOME must be writable, otherwise jgit fails to save its config.
HOME_DIR="$REPO_ROOT/.tmp/maven-home"
IMAGE="${DEV_CONTAINER_IMAGE:-maven:3.9-eclipse-temurin-21}"
PORT="${DEV_CONTAINER_PORT:-8080}"

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker not found. Install Docker, or use podman with an alias." >&2
    exit 1
fi

mkdir -p "$M2_DIR" "$HOME_DIR"

# Only dev mode claims the container name and publishes the port, so goals like
# `test` can still be run while dev mode is up.
SERVE_FLAGS=()
if [ "$#" -eq 0 ]; then
    # Bind to 0.0.0.0 so the port is reachable from outside the container.
    set -- quarkus:dev -Dquarkus.http.host=0.0.0.0
    SERVE_FLAGS=(--name homedir-dev -p "$PORT":8080)
fi

# Allocate a TTY only when there is one, so the script also works in CI.
TTY_FLAGS=(-i)
[ -t 0 ] && TTY_FLAGS+=(-t)

exec docker run --rm "${TTY_FLAGS[@]}" "${SERVE_FLAGS[@]}" \
    -u "$(id -u):$(id -g)" \
    -v "$REPO_ROOT":/project \
    -v "$HOME_DIR":/var/maven \
    -v "$M2_DIR":/var/maven/.m2 \
    -w /project/quarkus-app \
    -e MAVEN_CONFIG=/var/maven/.m2 \
    "$IMAGE" \
    mvn -Duser.home=/var/maven "$@"
