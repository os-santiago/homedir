#!/usr/bin/env bash
# Server-side bootstrap for the HomeDir autonomous SDLC runner.

set -euo pipefail

HOMEDIR_PLATFORM_REPO="${HOMEDIR_PLATFORM_REPO:-https://github.com/os-santiago/homedir.git}"
HOMEDIR_PLATFORM_REF="${HOMEDIR_PLATFORM_REF:-main}"
HOMEDIR_PLATFORM_DIR="${HOMEDIR_PLATFORM_DIR:-/opt/homedir-platform}"
PLAYBOOK="${HOMEDIR_PLATFORM_DIR}/platform/ansible/playbooks/sdlc-runner.yml"
INVENTORY="${HOMEDIR_PLATFORM_DIR}/platform/ansible/inventory.local.ini"

log() {
  printf '%s [homedir-sdlc-bootstrap] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root on the VPS." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

log "installing bootstrap packages"
apt-get update
apt-get install -y ca-certificates git python3 python3-venv python3-pip

if ! command -v ansible-playbook >/dev/null 2>&1; then
  log "installing ansible"
  apt-get install -y ansible
fi

if [[ ! -d "${HOMEDIR_PLATFORM_DIR}/.git" ]]; then
  log "cloning ${HOMEDIR_PLATFORM_REPO} into ${HOMEDIR_PLATFORM_DIR}"
  git clone "${HOMEDIR_PLATFORM_REPO}" "${HOMEDIR_PLATFORM_DIR}"
fi

log "updating platform checkout to ${HOMEDIR_PLATFORM_REF}"
git -C "${HOMEDIR_PLATFORM_DIR}" fetch origin "${HOMEDIR_PLATFORM_REF}" --tags
git -C "${HOMEDIR_PLATFORM_DIR}" checkout "${HOMEDIR_PLATFORM_REF}"
git -C "${HOMEDIR_PLATFORM_DIR}" pull --ff-only origin "${HOMEDIR_PLATFORM_REF}" || true

log "applying local SDLC runner playbook"
ansible-playbook -i "${INVENTORY}" "${PLAYBOOK}"

log "bootstrap complete"
log "next: configure gh auth and SCC provider credentials on this server"
