#!/usr/bin/env bash
set -euo pipefail
umask 077

REPO_URL_DEFAULT="https://github.com/os-santiago/homedir.git"
REPO_REF_DEFAULT="main"
WORK_ROOT_DEFAULT="/var/lib/homedir-dr"
ENV_TARGET_DEFAULT="/etc/homedir.env"
HEALTH_PATH_DEFAULT="/q/health"

REPO_URL="${REPO_URL_DEFAULT}"
REPO_REF="${REPO_REF_DEFAULT}"
WORK_ROOT="${WORK_ROOT_DEFAULT}"
ENV_SOURCE=""
AGE_IDENTITY=""
BACKUP_SOURCE=""
BACKUP_SHA256=""
BACKUP_SHA256_FILE=""
ENV_TARGET="${ENV_TARGET_DEFAULT}"
HOST_DATA_DIR=""
DEPLOY_TAG=""
HEALTH_PATH="${HEALTH_PATH_DEFAULT}"
HEALTH_TIMEOUT_SECONDS="300"
SKIP_DATA_RESTORE="false"
SKIP_NGINX="false"
ENABLE_WEBHOOK="false"
APPLY_HARDENING="false"
DRY_RUN="false"

usage() {
  cat <<'EOF'
Usage:
  homedir-dr-recover.sh [options]

Description:
  Rebuilds HomeDir runtime on an already provisioned VM:
  - pulls platform assets from GitHub
  - installs scripts/systemd/nginx assets
  - restores secure env file
  - restores application data from backup archive
  - deploys image from Quay and verifies health

Required:
  --env-file <path>            Plain or age-encrypted env file to install as /etc/homedir.env

Optional:
  --backup-file <path>         Backup archive (.zip/.tar/.tar.gz/.tgz) or .age encrypted archive
  --backup-sha256 <hex>        Expected sha256 for backup artifact
  --backup-sha256-file <path>  File containing expected sha256 (first field)
  --age-identity <path>        age private key file (required for *.age inputs)
  --repo-url <url>             GitHub repository URL (default: https://github.com/os-santiago/homedir.git)
  --repo-ref <ref>             Git ref/tag/branch to recover from (default: main)
  --work-root <path>           Work directory root (default: /var/lib/homedir-dr)
  --env-target <path>          Destination env path (default: /etc/homedir.env)
  --data-dir <path>            Host data directory (default: from DATA_VOLUME in env file)
  --deploy-tag <tag>           Quay tag to deploy (default: auto-detect latest semver with fallback script)
  --health-path <path>         Health endpoint path (default: /q/health)
  --health-timeout <seconds>   Health wait timeout (default: 300)
  --skip-data-restore          Skip backup restore stage
  --skip-nginx                 Skip nginx config deployment/reload
  --enable-webhook             Enable and start homedir-webhook.service
  --apply-hardening            Apply baseline VPS/app hardening after recovery steps
  --dry-run                    Print actions without changing the system
  -h, --help                   Show this help

Examples:
  homedir-dr-recover.sh \
    --env-file /secure/homedir.env.age \
    --age-identity /root/.config/age/keys.txt \
    --backup-file /secure/homedir-data-20260305T010000Z.tar.gz.age \
    --backup-sha256-file /secure/homedir-data-20260305T010000Z.tar.gz.age.sha256

  homedir-dr-recover.sh \
    --env-file /secure/homedir.env \
    --skip-data-restore \
    --deploy-tag v3.500.2
EOF
}

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

run_cmd() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: $*"
    return 0
  fi
  "$@"
}

need_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

secure_delete() {
  local file="$1"
  [[ -f "${file}" ]] || return 0
  if command -v shred >/dev/null 2>&1; then
    shred -u "${file}" >/dev/null 2>&1 || rm -f "${file}"
  else
    rm -f "${file}"
  fi
}

sha_from_file() {
  local file="$1"
  [[ -f "${file}" ]] || fail "checksum file not found: ${file}"
  awk '{print $1}' "${file}"
}

decrypt_if_needed() {
  local source_file="$1"
  local output_file="$2"
  if [[ "${source_file}" == *.age ]]; then
    [[ -n "${AGE_IDENTITY}" ]] || fail "age identity is required to decrypt ${source_file}"
    run_cmd age -d -i "${AGE_IDENTITY}" -o "${output_file}" "${source_file}"
  else
    run_cmd cp "${source_file}" "${output_file}"
  fi
}

parse_host_data_dir() {
  local env_file="$1"
  if [[ -n "${HOST_DATA_DIR}" ]]; then
    return 0
  fi
  # shellcheck disable=SC1090
  set -a && source "${env_file}" && set +a
  if [[ -n "${DATA_VOLUME:-}" ]]; then
    HOST_DATA_DIR="$(echo "${DATA_VOLUME}" | awk -F: '{print $1}')"
  fi
  if [[ -z "${HOST_DATA_DIR}" ]]; then
    HOST_DATA_DIR="/work/data"
  fi
}

validate_env_file() {
  local env_file="$1"
  local app_public_url
  [[ -f "${env_file}" ]] || fail "env file not found after restore: ${env_file}"
  if grep -Eq '^[A-Za-z_][A-Za-z0-9_]*=__[A-Za-z0-9_]+__$' "${env_file}"; then
    fail "env file still contains placeholder values (__...__). Provide production secrets."
  fi
  app_public_url="$(awk -F= '$1 == "APP_PUBLIC_URL" {sub(/^[^=]*=/, "", $0); print $0; exit}' "${env_file}")"
  if [[ -z "${app_public_url}" || ! "${app_public_url}" =~ ^https:// || "${app_public_url}" =~ localhost|127\.0\.0\.1 ]]; then
    fail "APP_PUBLIC_URL must be a public https endpoint (not localhost/127.0.0.1)"
  fi
}

wait_for_health() {
  local host_port="$1"
  local timeout_seconds="$2"
  local path="$3"
  local local_url="http://127.0.0.1:${host_port}${path}"
  local attempts=$(( timeout_seconds / 5 ))
  (( attempts < 1 )) && attempts=1

  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "${local_url}" >/dev/null 2>&1; then
      log "healthcheck passed: ${local_url}"
      return 0
    fi
    sleep 5
  done
  return 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_SOURCE="${2:-}"
      shift 2
      ;;
    --backup-file)
      BACKUP_SOURCE="${2:-}"
      shift 2
      ;;
    --backup-sha256)
      BACKUP_SHA256="${2:-}"
      shift 2
      ;;
    --backup-sha256-file)
      BACKUP_SHA256_FILE="${2:-}"
      shift 2
      ;;
    --age-identity)
      AGE_IDENTITY="${2:-}"
      shift 2
      ;;
    --repo-url)
      REPO_URL="${2:-}"
      shift 2
      ;;
    --repo-ref)
      REPO_REF="${2:-}"
      shift 2
      ;;
    --work-root)
      WORK_ROOT="${2:-}"
      shift 2
      ;;
    --env-target)
      ENV_TARGET="${2:-}"
      shift 2
      ;;
    --data-dir)
      HOST_DATA_DIR="${2:-}"
      shift 2
      ;;
    --deploy-tag)
      DEPLOY_TAG="${2:-}"
      shift 2
      ;;
    --health-path)
      HEALTH_PATH="${2:-}"
      shift 2
      ;;
    --health-timeout)
      HEALTH_TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    --skip-data-restore)
      SKIP_DATA_RESTORE="true"
      shift
      ;;
    --skip-nginx)
      SKIP_NGINX="true"
      shift
      ;;
    --enable-webhook)
      ENABLE_WEBHOOK="true"
      shift
      ;;
    --apply-hardening)
      APPLY_HARDENING="true"
      shift
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ "${EUID}" -eq 0 ]] || fail "run as root"
[[ -n "${ENV_SOURCE}" ]] || fail "--env-file is required"
[[ -f "${ENV_SOURCE}" ]] || fail "env source not found: ${ENV_SOURCE}"
if [[ "${SKIP_DATA_RESTORE}" != "true" ]]; then
  [[ -n "${BACKUP_SOURCE}" ]] || fail "--backup-file is required unless --skip-data-restore is set"
  [[ -f "${BACKUP_SOURCE}" ]] || fail "backup source not found: ${BACKUP_SOURCE}"
fi

need_cmd git
need_cmd install
need_cmd systemctl
need_cmd rsync
need_cmd podman
need_cmd curl
need_cmd sha256sum
need_cmd python3
if [[ "${ENV_SOURCE}" == *.age || "${BACKUP_SOURCE}" == *.age ]]; then
  need_cmd age
  [[ -n "${AGE_IDENTITY}" ]] || fail "--age-identity is required for *.age files"
  [[ -f "${AGE_IDENTITY}" ]] || fail "age identity file not found: ${AGE_IDENTITY}"
fi

lockdir="/var/lock/homedir-dr-recover.lock.d"
if ! mkdir "${lockdir}" 2>/dev/null; then
  fail "another DR recovery process is running"
fi
cleanup_lock() { rmdir "${lockdir}" 2>/dev/null || true; }
trap cleanup_lock EXIT

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
workdir="${WORK_ROOT}/run-${timestamp}"
repo_dir="${workdir}/src"
tmp_env="${workdir}/homedir.env"
tmp_backup="${workdir}/backup.raw"
restore_stage="${workdir}/restore-stage"

log "starting DR recovery run ${timestamp}"
run_cmd mkdir -p "${workdir}"

log "cloning repository ${REPO_URL} ref=${REPO_REF}"
run_cmd git clone "${REPO_URL}" "${repo_dir}"
run_cmd git -C "${repo_dir}" checkout "${REPO_REF}"

platform_dir="${repo_dir}/platform"
if [[ ! -d "${platform_dir}" ]]; then
  if [[ "${DRY_RUN}" == "true" ]]; then
    log "WARNING: platform directory check skipped in dry-run mode (clone is simulated)"
  else
    fail "platform directory not found in repository checkout"
  fi
fi

log "installing platform scripts and systemd units"
for script in \
  homedir-env-lib.sh \
  homedir-update.sh \
  homedir-auto-deploy.sh \
  homedir-discord-alert.sh \
  homedir-ir-first-level.sh \
  homedir-cfp-traffic-guard.sh \
  homedir-security-hardening.sh \
  homedir-secrets-rotate.sh \
  homedir-dr-backup.sh \
  homedir-dr-recover.sh \
  homedir-dr-restore.py; do
  run_cmd install -m 0755 "${platform_dir}/scripts/${script}" "/usr/local/bin/${script}"
done
run_cmd install -m 0755 "${platform_dir}/scripts/homedir-webhook.py" "/usr/local/bin/homedir-webhook.py"
for unit in \
  homedir-auto-deploy.service \
  homedir-auto-deploy.timer \
  homedir-update.service \
  homedir-update.timer \
  homedir-cfp-traffic-guard.service \
  homedir-cfp-traffic-guard.timer \
  homedir-webhook.service; do
  run_cmd install -m 0644 "${platform_dir}/systemd/${unit}" "/etc/systemd/system/${unit}"
done
run_cmd systemctl daemon-reload

if [[ "${SKIP_NGINX}" != "true" ]]; then
  if [[ -d /etc/nginx/sites-available && -d /etc/nginx/sites-enabled ]]; then
    log "installing nginx configuration"
    run_cmd install -m 0644 "${platform_dir}/nginx/homedir.conf" /etc/nginx/sites-available/homedir.conf
    run_cmd install -m 0644 "${platform_dir}/nginx/int.conf" /etc/nginx/sites-available/int.conf
    run_cmd mkdir -p /etc/nginx/snippets
    run_cmd install -m 0644 "${platform_dir}/nginx/snippets/homedir-incident-guard.conf" /etc/nginx/snippets/homedir-incident-guard.conf
    run_cmd install -m 0644 "${platform_dir}/nginx/snippets/homedir-security-hardening.conf" /etc/nginx/snippets/homedir-security-hardening.conf
    run_cmd mkdir -p /var/www/html
    run_cmd install -m 0644 "${platform_dir}/assets/maintenance.html" /var/www/html/maintenance.html
    run_cmd ln -sfn /etc/nginx/sites-available/homedir.conf /etc/nginx/sites-enabled/homedir.conf
    run_cmd ln -sfn /etc/nginx/sites-available/int.conf /etc/nginx/sites-enabled/int.conf
    if [[ "${DRY_RUN}" == "true" ]]; then
      echo "DRY-RUN: nginx -t && systemctl reload nginx"
    else
      nginx -t
      systemctl reload nginx
    fi
  else
    log "nginx sites-available/sites-enabled not found; skipping nginx asset install"
  fi
fi

log "installing secure env file at ${ENV_TARGET}"
if [[ "${DRY_RUN}" == "true" ]]; then
  log "DRY-RUN: skipping decrypted env materialization and placeholder validation"
  echo "DRY-RUN: install -D -m 0600 ${ENV_SOURCE} ${ENV_TARGET}"
else
  decrypt_if_needed "${ENV_SOURCE}" "${tmp_env}"
  validate_env_file "${tmp_env}"
  run_cmd install -D -m 0600 "${tmp_env}" "${ENV_TARGET}"
  secure_delete "${tmp_env}"
fi

parse_host_data_dir "${ENV_TARGET}"
[[ -n "${HOST_DATA_DIR}" ]] || fail "failed to resolve host data directory"
log "resolved host data directory: ${HOST_DATA_DIR}"

if [[ "${SKIP_DATA_RESTORE}" != "true" ]]; then
  log "preparing backup artifact"
  decrypt_if_needed "${BACKUP_SOURCE}" "${tmp_backup}"

  expected_sha=""
  if [[ -n "${BACKUP_SHA256}" ]]; then
    expected_sha="${BACKUP_SHA256}"
  elif [[ -n "${BACKUP_SHA256_FILE}" ]]; then
    expected_sha="$(sha_from_file "${BACKUP_SHA256_FILE}")"
  elif [[ -f "${BACKUP_SOURCE}.sha256" ]]; then
    expected_sha="$(sha_from_file "${BACKUP_SOURCE}.sha256")"
  fi

  if [[ -n "${expected_sha}" ]]; then
    actual_sha="$(sha256sum "${tmp_backup}" | awk '{print $1}')"
    [[ "${actual_sha}" == "${expected_sha}" ]] || fail "backup checksum mismatch"
    log "backup checksum validated"
  else
    log "WARNING: no backup checksum provided; continuing without integrity verification"
  fi

  run_cmd mkdir -p "${restore_stage}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: /usr/local/bin/homedir-dr-restore.py --archive ${tmp_backup} --output-dir ${restore_stage}"
  else
    /usr/local/bin/homedir-dr-restore.py --archive "${tmp_backup}" --output-dir "${restore_stage}"
  fi

  if [[ -d "${HOST_DATA_DIR}" ]] && [[ -n "$(ls -A "${HOST_DATA_DIR}" 2>/dev/null || true)" ]]; then
    previous_dir="${HOST_DATA_DIR}.pre-dr-${timestamp}"
    log "preserving existing data directory as ${previous_dir}"
    run_cmd mv "${HOST_DATA_DIR}" "${previous_dir}"
  fi
  run_cmd mkdir -p "${HOST_DATA_DIR}"
  run_cmd rsync -a --delete "${restore_stage}/" "${HOST_DATA_DIR}/"
  secure_delete "${tmp_backup}"
fi

log "enabling fallback auto-deploy timer"
run_cmd systemctl enable --now homedir-auto-deploy.timer
run_cmd systemctl enable --now homedir-cfp-traffic-guard.timer
if [[ "${ENABLE_WEBHOOK}" == "true" ]]; then
  run_cmd systemctl enable --now homedir-webhook.service
fi

if [[ "${APPLY_HARDENING}" == "true" ]]; then
  log "applying baseline hardening"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: /usr/local/bin/homedir-security-hardening.sh apply"
  else
    /usr/local/bin/homedir-security-hardening.sh apply
  fi
fi

if [[ -n "${DEPLOY_TAG}" ]]; then
  log "deploying requested tag ${DEPLOY_TAG}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: DEPLOY_TRIGGER=dr /usr/local/bin/homedir-update.sh ${DEPLOY_TAG}"
  else
    DEPLOY_TRIGGER=dr /usr/local/bin/homedir-update.sh "${DEPLOY_TAG}"
  fi
else
  log "deploying latest semver tag from Quay via fallback deploy script"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY-RUN: DEPLOY_TRIGGER=dr /usr/local/bin/homedir-auto-deploy.sh"
  else
    DEPLOY_TRIGGER=dr /usr/local/bin/homedir-auto-deploy.sh
  fi
fi

# shellcheck disable=SC1090
set -a && source "${ENV_TARGET}" && set +a
host_port="${HOST_PORT:-8080}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "DRY-RUN: wait_for_health host_port=${host_port} timeout=${HEALTH_TIMEOUT_SECONDS} path=${HEALTH_PATH}"
else
  if ! wait_for_health "${host_port}" "${HEALTH_TIMEOUT_SECONDS}" "${HEALTH_PATH}"; then
    fail "healthcheck failed after recovery"
  fi
fi

log "DR recovery completed successfully"
log "workdir: ${workdir}"
