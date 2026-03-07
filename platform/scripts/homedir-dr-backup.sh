#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_LIB="${HOMEDIR_ENV_LIB:-${SCRIPT_DIR}/homedir-env-lib.sh}"
ENV_FILE_DEFAULT="/etc/homedir.env"
OUTPUT_DIR_DEFAULT="/var/backups/homedir-dr"

ENV_FILE="${ENV_FILE_DEFAULT}"
OUTPUT_DIR="${OUTPUT_DIR_DEFAULT}"
DATA_DIR=""
LABEL=""
ALLOW_PLAINTEXT="false"
DRY_RUN="false"
declare -a AGE_RECIPIENTS=()

if [[ ! -f "${ENV_LIB}" ]]; then
  ENV_LIB="/usr/local/bin/homedir-env-lib.sh"
fi
# shellcheck source=/dev/null
source "${ENV_LIB}"

usage() {
  cat <<'EOF'
Usage:
  homedir-dr-backup.sh [options]

Options:
  --env-file <path>           Environment file (default: /etc/homedir.env)
  --data-dir <path>           Host data directory to back up (default: from DATA_VOLUME or /work/data)
  --output-dir <path>         Output directory (default: /var/backups/homedir-dr)
  --label <text>              Optional label for artifact naming
  --age-recipient <value>     age recipient (repeatable) to encrypt archive
  --allow-plaintext           Allow non-encrypted archive output (not recommended)
  --dry-run                   Print planned actions without writing files
  -h, --help                  Show this help

Examples:
  homedir-dr-backup.sh --age-recipient age1... --label pre-maintenance
  homedir-dr-backup.sh --data-dir /srv/homedir --output-dir /mnt/secure-backups --age-recipient age1...
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

resolve_data_dir_from_env() {
  homedir_env_load "${ENV_FILE}"

  if [[ -n "${DATA_DIR}" ]]; then
    return 0
  fi

  if [[ -n "${DATA_VOLUME:-}" ]]; then
    DATA_DIR="$(echo "${DATA_VOLUME}" | awk -F: '{print $1}')"
  fi

  if [[ -z "${DATA_DIR}" ]]; then
    DATA_DIR="/work/data"
  fi
}

safe_label() {
  local raw="$1"
  if [[ -z "${raw}" ]]; then
    echo ""
    return
  fi
  echo "${raw}" | tr -cs 'a-zA-Z0-9._-' '-'
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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --data-dir)
      DATA_DIR="${2:-}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --age-recipient)
      AGE_RECIPIENTS+=("${2:-}")
      shift 2
      ;;
    --allow-plaintext)
      ALLOW_PLAINTEXT="true"
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

need_cmd tar
need_cmd sha256sum

if [[ "${#AGE_RECIPIENTS[@]}" -gt 0 ]]; then
  need_cmd age
elif [[ "${ALLOW_PLAINTEXT}" != "true" ]]; then
  fail "encryption is required by default. Provide --age-recipient or explicitly use --allow-plaintext"
fi

resolve_data_dir_from_env

[[ -d "${DATA_DIR}" ]] || fail "data directory does not exist: ${DATA_DIR}"
[[ -n "${OUTPUT_DIR}" ]] || fail "--output-dir is required"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
label_suffix="$(safe_label "${LABEL}")"
if [[ -n "${label_suffix}" ]]; then
  base_name="homedir-data-${timestamp}-${label_suffix}"
else
  base_name="homedir-data-${timestamp}"
fi

workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

archive_plain="${workdir}/${base_name}.tar.gz"
artifact="${OUTPUT_DIR}/${base_name}.tar.gz"
metadata="${OUTPUT_DIR}/${base_name}.metadata.json"

log "creating backup archive from ${DATA_DIR}"
run_cmd mkdir -p "${OUTPUT_DIR}"
run_cmd tar -C "${DATA_DIR}" -czf "${archive_plain}" .

if [[ "${#AGE_RECIPIENTS[@]}" -gt 0 ]]; then
  artifact="${OUTPUT_DIR}/${base_name}.tar.gz.age"
  age_args=()
  for recipient in "${AGE_RECIPIENTS[@]}"; do
    [[ -n "${recipient}" ]] || fail "empty --age-recipient is not allowed"
    age_args+=("-r" "${recipient}")
  done
  log "encrypting backup archive with age"
  run_cmd age "${age_args[@]}" -o "${artifact}" "${archive_plain}"
  secure_delete "${archive_plain}"
else
  log "writing plaintext backup archive (encryption disabled)"
  run_cmd mv "${archive_plain}" "${artifact}"
fi

log "writing checksum and metadata"
if [[ "${DRY_RUN}" == "true" ]]; then
  echo "DRY-RUN: sha256sum \"${artifact}\" > \"${artifact}.sha256\""
else
  sha256sum "${artifact}" > "${artifact}.sha256"
fi

if [[ "${DRY_RUN}" == "true" ]]; then
  cat <<EOF
DRY-RUN: metadata would be written to ${metadata}
{
  "created_at_utc": "${timestamp}",
  "artifact_name": "$(basename "${artifact}")",
  "checksum_file_name": "$(basename "${artifact}.sha256")",
  "checksum_algorithm": "sha256",
  "encrypted": $([[ "${#AGE_RECIPIENTS[@]}" -gt 0 ]] && echo "true" || echo "false")
}
EOF
else
  cat > "${metadata}" <<EOF
{
  "created_at_utc": "${timestamp}",
  "artifact_name": "$(basename "${artifact}")",
  "checksum_file_name": "$(basename "${artifact}.sha256")",
  "checksum_algorithm": "sha256",
  "encrypted": $([[ "${#AGE_RECIPIENTS[@]}" -gt 0 ]] && echo "true" || echo "false")
}
EOF
fi

log "backup generated: ${artifact}"
log "checksum file: ${artifact}.sha256"
log "metadata file: ${metadata}"
