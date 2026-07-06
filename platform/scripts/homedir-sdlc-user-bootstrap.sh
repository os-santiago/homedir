#!/usr/bin/env bash
# User-owned server-side bootstrap for the HomeDir autonomous SDLC runner.

set -euo pipefail

HOMEDIR_PLATFORM_REPO="${HOMEDIR_PLATFORM_REPO:-https://github.com/os-santiago/homedir.git}"
HOMEDIR_PLATFORM_REF="${HOMEDIR_PLATFORM_REF:-main}"
HOMEDIR_PLATFORM_DIR="${HOMEDIR_PLATFORM_DIR:-$HOME/.local/share/homedir-platform}"
SC_AGENT_REPO="${SC_AGENT_REPO:-https://github.com/os-santiago/sc-agent-cli.git}"
SC_AGENT_REF="${SC_AGENT_REF:-main}"
SC_AGENT_DIR="${SC_AGENT_DIR:-$HOME/.local/share/sc-agent-cli}"
NODE_VERSION="${NODE_VERSION:-20.19.5}"
NODE_DIR="${NODE_DIR:-$HOME/.local/opt/node-v${NODE_VERSION}-linux-x64}"
GH_VERSION="${GH_VERSION:-2.74.2}"
JQ_VERSION="${JQ_VERSION:-1.7.1}"
LOCAL_BIN="${LOCAL_BIN:-$HOME/.local/bin}"
CONFIG_DIR="${CONFIG_DIR:-$HOME/.config/homedir-sdlc}"
STATE_DIR="${STATE_DIR:-$HOME/.local/state/homedir-sdlc}"
WORKDIR="${WORKDIR:-$HOME/.local/share/homedir-sdlc/worktrees/homedir}"
LOG_DIR="${LOG_DIR:-$HOME/.local/state/homedir-sdlc/logs}"
SYSTEMD_USER_DIR="${SYSTEMD_USER_DIR:-$HOME/.config/systemd/user}"

log() {
  printf '%s [homedir-sdlc-user-bootstrap] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

clone_or_update() {
  local repo="$1"
  local ref="$2"
  local dest="$3"
  if [[ -d "${dest}" && ! -d "${dest}/.git" ]]; then
    log "using existing non-git snapshot at ${dest}"
    return 0
  fi

  if [[ ! -d "${dest}/.git" ]]; then
    log "cloning ${repo} into ${dest}"
    mkdir -p "$(dirname "${dest}")"
    git clone "${repo}" "${dest}"
  fi
  log "updating ${dest} to ${ref}"
  git -C "${dest}" fetch origin "${ref}" --tags
  git -C "${dest}" checkout "${ref}"
  git -C "${dest}" pull --ff-only origin "${ref}"
}

verify_sha256() {
  local file="$1"
  local expected="$2"
  local actual
  actual="$(sha256sum "${file}" | cut -d' ' -f1)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "SHA256 mismatch for ${file}: expected ${expected}, got ${actual}" >&2
    exit 1
  fi
}

install_node() {
  if [[ -x "${NODE_DIR}/bin/node" ]]; then
    return 0
  fi

  local archive tmp sha
  archive="node-v${NODE_VERSION}-linux-x64.tar.xz"
  tmp="$(mktemp -d)"
  log "installing Node.js ${NODE_VERSION} under ${NODE_DIR}"
  curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/${archive}" -o "${tmp}/${archive}"
  if ! curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt" -o "${tmp}/SHASUMS256.txt"; then
    echo "ERROR: failed to download SHASUMS256.txt for Node.js ${NODE_VERSION}" >&2
    exit 1
  fi
  sha="$(grep "${archive}" "${tmp}/SHASUMS256.txt" | cut -d' ' -f1)"
  if [[ -z "${sha}" ]]; then
    echo "ERROR: checksum for ${archive} not found in SHASUMS256.txt" >&2
    exit 1
  fi
  verify_sha256 "${tmp}/${archive}" "${sha}"
  mkdir -p "$(dirname "${NODE_DIR}")"
  tar -xJf "${tmp}/${archive}" -C "$(dirname "${NODE_DIR}")"
  rm -rf "${tmp}"
}

install_jq() {
  if [[ -x "${LOCAL_BIN}/jq" ]]; then
    return 0
  fi

  local arch jq_asset
  arch="$(uname -m)"
  case "${arch}" in
    x86_64|amd64)
      jq_asset="jq-linux-amd64"
      ;;
    aarch64|arm64)
      jq_asset="jq-linux-arm64"
      ;;
    *)
      echo "Unsupported architecture for jq bootstrap: ${arch}" >&2
      exit 1
      ;;
  esac

  mkdir -p "${LOCAL_BIN}"
  log "installing jq ${JQ_VERSION} under ${LOCAL_BIN}"
  curl -fsSL "https://github.com/jqlang/jq/releases/download/jq-${JQ_VERSION}/${jq_asset}" -o "${LOCAL_BIN}/jq"
  if ! curl -fsSL "https://github.com/jqlang/jq/releases/download/jq-${JQ_VERSION}/sha256sum.txt" -o "${LOCAL_BIN}/jq.sha256"; then
    echo "ERROR: failed to download sha256sum.txt for jq ${JQ_VERSION}" >&2
    exit 1
  fi
  sha="$(grep "${jq_asset}" "${LOCAL_BIN}/jq.sha256" | cut -d' ' -f1)"
  if [[ -z "${sha}" ]]; then
    echo "ERROR: checksum for ${jq_asset} not found in sha256sum.txt" >&2
    exit 1
  fi
  verify_sha256 "${LOCAL_BIN}/jq" "${sha}"
  rm -f "${LOCAL_BIN}/jq.sha256"
  chmod 0755 "${LOCAL_BIN}/jq"
}

install_gh() {
  if command -v gh >/dev/null 2>&1 || [[ -x "${LOCAL_BIN}/gh" ]]; then
    return 0
  fi

  local arch gh_arch archive tmp
  arch="$(uname -m)"
  case "${arch}" in
    x86_64|amd64)
      gh_arch="linux_amd64"
      ;;
    aarch64|arm64)
      gh_arch="linux_arm64"
      ;;
    *)
      echo "Unsupported architecture for gh bootstrap: ${arch}" >&2
      exit 1
      ;;
  esac

  archive="gh_${GH_VERSION}_${gh_arch}.tar.gz"
  tmp="$(mktemp -d)"
  log "installing GitHub CLI ${GH_VERSION} under ${LOCAL_BIN}"
  curl -fsSL "https://github.com/cli/cli/releases/download/v${GH_VERSION}/${archive}" -o "${tmp}/${archive}"
  if ! curl -fsSL "https://github.com/cli/cli/releases/download/v${GH_VERSION}/gh_${GH_VERSION}_checksums.txt" -o "${tmp}/checksums.txt"; then
    echo "ERROR: failed to download checksums for gh ${GH_VERSION}" >&2
    exit 1
  fi
  sha="$(grep "${archive}" "${tmp}/checksums.txt" | cut -d' ' -f1)"
  if [[ -z "${sha}" ]]; then
    echo "ERROR: checksum for ${archive} not found in gh checksums" >&2
    exit 1
  fi
  verify_sha256 "${tmp}/${archive}" "${sha}"
  tar -xzf "${tmp}/${archive}" -C "${tmp}"
  mkdir -p "${LOCAL_BIN}"
  install -m 0755 "${tmp}/gh_${GH_VERSION}_${gh_arch}/bin/gh" "${LOCAL_BIN}/gh"
  rm -rf "${tmp}"
}

write_env() {
  local env_file="${CONFIG_DIR}/env"
  mkdir -p "${CONFIG_DIR}" "${STATE_DIR}" "${LOG_DIR}" "$(dirname "${WORKDIR}")"
  if [[ -f "${env_file}" ]]; then
    log "keeping existing ${env_file}"
    return 0
  fi

  log "writing ${env_file}"
  cat >"${env_file}" <<EOF
PATH=${NODE_DIR}/bin:${LOCAL_BIN}:/usr/local/bin:/usr/bin:/bin
HOMEDIR_SDLC_REPO=os-santiago/homedir
HOMEDIR_SDLC_TRIGGER_LABEL=ready-to-implement
HOMEDIR_SDLC_QUEUE_LABEL=scc-queued
HOMEDIR_SDLC_REJECTED_LABEL=scc-rejected
HOMEDIR_SDLC_UNAUTHORIZED_LABEL=scc-rejected:unauthorized-labeler
HOMEDIR_SDLC_AUTHORIZED_LABELERS=scanalesespinoza
HOMEDIR_SDLC_ADMISSION_REVIEW_LABEL=scc-admission-review
HOMEDIR_SDLC_ACCEPTED_LABEL=scc-accepted
HOMEDIR_SDLC_RUNNING_LABEL=scc-running
HOMEDIR_SDLC_PR_LABEL=scc-pr-open
HOMEDIR_SDLC_PR_TRACK_LABEL=ai-sdlc-track
HOMEDIR_SDLC_PR_ASSIST_LABEL=ai-sdlc-assist
HOMEDIR_SDLC_WAITING_CHECKS_LABEL=scc-waiting-checks
HOMEDIR_SDLC_FAILING_CHECKS_LABEL=scc-failing-checks
HOMEDIR_SDLC_UNDER_REVIEW_LABEL=scc-under-review
HOMEDIR_SDLC_COVERAGE_GAP_LABEL=scc-coverage-gap
HOMEDIR_SDLC_APPROVED_LABEL=scc-approved
HOMEDIR_SDLC_FAILED_LABEL=scc-failed
HOMEDIR_SDLC_NEEDS_HUMAN_LABEL=needs-human
HOMEDIR_SDLC_MERGED_LABEL=scc-merged
HOMEDIR_SDLC_WORKDIR=${WORKDIR}
HOMEDIR_SDLC_STATE_DIR=${STATE_DIR}
HOMEDIR_SDLC_LOGFILE=${LOG_DIR}/worker.log
HOMEDIR_SDLC_HEARTBEAT_FILE=${STATE_DIR}/heartbeat.json
HOMEDIR_SDLC_HEARTBEAT_MAX_AGE_SECONDS=900
HOMEDIR_SDLC_MAX_ISSUES_PER_RUN=1
HOMEDIR_SDLC_MAX_REMEDIATION_ATTEMPTS=5
HOMEDIR_SDLC_PR_REVIEW_DELAY_SECONDS=600
HOMEDIR_SDLC_ENABLE_AUTOMERGE=false
SCC_BIN=${LOCAL_BIN}/scc
HOMEDIR_SDLC_SCC_TIMEOUT_SECONDS=1800
HOMEDIR_SDLC_SCC_PROFILE=nvidia
HOMEDIR_SDLC_SCC_CLEAR_HISTORY=true
HOMEDIR_SDLC_SCC_PERMISSIONS=unlimited
HOMEDIR_SDLC_WORKER_BIN=${LOCAL_BIN}/homedir-sdlc-worker.sh
HOMEDIR_SDLC_OPENCLAW_LOGFILE=${LOG_DIR}/openclaw-listener.log
HOMEDIR_SDLC_ALERTS_ENABLED=false
HOMEDIR_SDLC_ALERT_WEBHOOK_URL_FILE=

# WARNING: Do not place GitHub tokens or secrets directly in this file.
# Prefer gh auth login, systemd credentials, or a secret manager.
# If you must use GH_TOKEN, set it in a separate secure EnvironmentFile
# with mode 0600 and ensure it is excluded from backups.
EOF
  chmod 0600 "${env_file}"
}

install_scc() {
  export PATH="${NODE_DIR}/bin:${PATH}"
  log "installing SCC dependencies"
  npm ci --prefix "${SC_AGENT_DIR}"
  npm run build --prefix "${SC_AGENT_DIR}"

  mkdir -p "${LOCAL_BIN}"
  cat >"${LOCAL_BIN}/scc" <<EOF
#!/usr/bin/env bash
export PATH="${NODE_DIR}/bin:\$PATH"
exec "${NODE_DIR}/bin/node" "${SC_AGENT_DIR}/bin/sc.js" "\$@"
EOF
  chmod 0755 "${LOCAL_BIN}/scc"
}

install_worker() {
  mkdir -p "${LOCAL_BIN}" "${SYSTEMD_USER_DIR}"
  install -m 0755 "${HOMEDIR_PLATFORM_DIR}/platform/scripts/homedir-sdlc-worker.sh" "${LOCAL_BIN}/homedir-sdlc-worker.sh"
  install -m 0755 "${HOMEDIR_PLATFORM_DIR}/platform/scripts/homedir-sdlc-openclaw-listener.sh" "${LOCAL_BIN}/homedir-sdlc-openclaw-listener.sh"
  install -m 0755 "${HOMEDIR_PLATFORM_DIR}/platform/scripts/homedir-sdlc-status.sh" "${LOCAL_BIN}/homedir-sdlc-status.sh"
  install -m 0644 "${HOMEDIR_PLATFORM_DIR}/platform/systemd/user/homedir-sdlc-worker.service" "${SYSTEMD_USER_DIR}/homedir-sdlc-worker.service"
  install -m 0644 "${HOMEDIR_PLATFORM_DIR}/platform/systemd/user/homedir-sdlc-worker.timer" "${SYSTEMD_USER_DIR}/homedir-sdlc-worker.timer"
}

enable_timer() {
  if ! systemctl --user is-system-running >/dev/null 2>&1; then
    log "systemd user manager is not available; installed files but did not enable timer"
    return 0
  fi
  systemctl --user daemon-reload
  systemctl --user enable --now homedir-sdlc-worker.timer
}

main() {
  need_cmd git
  need_cmd curl
  need_cmd tar
  clone_or_update "${HOMEDIR_PLATFORM_REPO}" "${HOMEDIR_PLATFORM_REF}" "${HOMEDIR_PLATFORM_DIR}"
  clone_or_update "${SC_AGENT_REPO}" "${SC_AGENT_REF}" "${SC_AGENT_DIR}"
  install_gh
  install_node
  install_jq
  write_env
  install_scc
  install_worker
  enable_timer

  log "bootstrap complete"
  log "server-owned files:"
  log "  platform=${HOMEDIR_PLATFORM_DIR}"
  log "  scc=${SC_AGENT_DIR}"
  log "  env=${CONFIG_DIR}/env"
  log "  state=${STATE_DIR}"
  log "  workdir=${WORKDIR}"
  log "next: configure gh auth and SCC provider credentials on this server"
}

main "$@"
