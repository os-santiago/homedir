#!/usr/bin/env bash
set -euo pipefail
umask 077

ENV_FILE="/etc/homedir.env"
INCIDENT_LOG_DIR="/var/log/homedir-incident"
BACKUP_DIR="/var/backups/homedir-dr"
INCIDENT_LOCK_FILE="/etc/homedir.incident.lock"
SYSCTL_FILE="/etc/sysctl.d/99-homedir-network-hardening.conf"
DRY_RUN="false"

AUDIT_FAILS=0
AUDIT_WARNS=0

usage() {
  cat <<'EOF'
Usage:
  homedir-security-hardening.sh <audit|apply> [options]

Commands:
  audit                      Validate baseline hardening controls and print findings.
  apply                      Apply non-destructive hardening baseline, then run audit.

Options:
  --env-file <path>          Environment file path (default: /etc/homedir.env)
  --incident-log-dir <path>  Incident log directory (default: /var/log/homedir-incident)
  --backup-dir <path>        Backup directory (default: /var/backups/homedir-dr)
  --sysctl-file <path>       Sysctl drop-in file (default: /etc/sysctl.d/99-homedir-network-hardening.conf)
  --dry-run                  Print intended actions without changing the host
  -h, --help                 Show this help

Examples:
  /usr/local/bin/homedir-security-hardening.sh audit
  /usr/local/bin/homedir-security-hardening.sh apply --dry-run
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
  command -v "${cmd}" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

print_check() {
  local level="$1"
  local message="$2"
  case "${level}" in
    PASS)
      echo "[PASS] ${message}"
      ;;
    WARN)
      echo "[WARN] ${message}"
      AUDIT_WARNS=$((AUDIT_WARNS + 1))
      ;;
    FAIL)
      echo "[FAIL] ${message}"
      AUDIT_FAILS=$((AUDIT_FAILS + 1))
      ;;
    *)
      echo "[INFO] ${message}"
      ;;
  esac
}

env_value() {
  local key="$1"
  if [[ ! -f "${ENV_FILE}" ]]; then
    return 1
  fi
  awk -F= -v k="${key}" '$1 == k {sub(/^[^=]*=/, "", $0); print $0; exit}' "${ENV_FILE}"
}

is_placeholder_value() {
  local val="$1"
  [[ "${val}" =~ __[A-Z0-9_]+__ ]]
}

check_mode_owner_group() {
  local path="$1"
  local expect_mode="$2"
  local expect_owner="$3"
  local expect_group="$4"
  if [[ ! -e "${path}" ]]; then
    print_check "FAIL" "${path} missing"
    return
  fi
  local mode owner group
  mode="$(stat -c '%a' "${path}")"
  owner="$(stat -c '%U' "${path}")"
  group="$(stat -c '%G' "${path}")"
  if [[ "${mode}" == "${expect_mode}" && "${owner}" == "${expect_owner}" && "${group}" == "${expect_group}" ]]; then
    print_check "PASS" "${path} permissions ${mode} owner ${owner}:${group}"
  else
    print_check "FAIL" "${path} expected ${expect_mode} ${expect_owner}:${expect_group}, found ${mode} ${owner}:${group}"
  fi
}

check_dir_restricted() {
  local path="$1"
  if [[ ! -d "${path}" ]]; then
    print_check "WARN" "${path} does not exist yet"
    return
  fi
  local mode
  mode="$(stat -c '%a' "${path}")"
  if [[ "${mode}" == "700" ]]; then
    print_check "PASS" "${path} permissions ${mode}"
  else
    print_check "WARN" "${path} should use mode 700 (current ${mode})"
  fi
}

unit_exists() {
  local unit="$1"
  systemctl list-unit-files --no-legend "${unit}" 2>/dev/null | awk '{print $1}' | grep -Fxq "${unit}"
}

check_service_active() {
  local svc="$1"
  if ! unit_exists "${svc}"; then
    print_check "WARN" "${svc} is not installed"
    return
  fi
  if systemctl is-enabled --quiet "${svc}" && systemctl is-active --quiet "${svc}"; then
    print_check "PASS" "${svc} enabled and active"
  else
    print_check "WARN" "${svc} not both enabled and active"
  fi
}

check_timer_active() {
  local timer="$1"
  if ! unit_exists "${timer}"; then
    print_check "FAIL" "${timer} not installed"
    return
  fi
  if systemctl is-enabled --quiet "${timer}" && systemctl is-active --quiet "${timer}"; then
    print_check "PASS" "${timer} enabled and active"
  else
    print_check "FAIL" "${timer} must be enabled and active"
  fi
}

check_firewall() {
  if systemctl list-unit-files firewalld.service >/dev/null 2>&1; then
    if systemctl is-active --quiet firewalld; then
      print_check "PASS" "firewalld active"
      return
    fi
    print_check "WARN" "firewalld installed but inactive"
    return
  fi
  if command -v ufw >/dev/null 2>&1; then
    if ufw status 2>/dev/null | grep -qi "status: active"; then
      print_check "PASS" "ufw active"
    else
      print_check "WARN" "ufw installed but inactive"
    fi
    return
  fi
  print_check "WARN" "no known host firewall tool detected (firewalld/ufw)"
}

check_sshd_baseline() {
  local sshd_config="/etc/ssh/sshd_config"
  if [[ ! -f "${sshd_config}" ]]; then
    print_check "WARN" "sshd_config not found"
    return
  fi

  if grep -Eq '^\s*PermitRootLogin\s+(no|prohibit-password)\s*$' "${sshd_config}"; then
    print_check "PASS" "sshd root login is restricted"
  else
    print_check "WARN" "sshd PermitRootLogin should be no/prohibit-password"
  fi

  if grep -Eq '^\s*PasswordAuthentication\s+no\s*$' "${sshd_config}"; then
    print_check "PASS" "sshd password authentication disabled"
  else
    print_check "WARN" "sshd PasswordAuthentication should be no (use keys only)"
  fi
}

run_audit() {
  need_cmd stat
  need_cmd grep
  need_cmd systemctl

  echo "== HomeDir security hardening audit =="
  check_mode_owner_group "${ENV_FILE}" "600" "root" "root"
  check_dir_restricted "${INCIDENT_LOG_DIR}"
  check_dir_restricted "${BACKUP_DIR}"

  if [[ -f "${INCIDENT_LOCK_FILE}" ]]; then
    check_mode_owner_group "${INCIDENT_LOCK_FILE}" "600" "root" "root"
  else
    print_check "PASS" "${INCIDENT_LOCK_FILE} absent (expected when shield is off)"
  fi

  if [[ -f "${ENV_FILE}" ]]; then
    if grep -q "__" "${ENV_FILE}"; then
      print_check "FAIL" "env file contains placeholder values (__...__)"
    else
      print_check "PASS" "env file has no placeholders"
    fi

    local webhook_secret webhook_token webhook_signature
    webhook_secret="$(env_value "WEBHOOK_SHARED_SECRET" || true)"
    webhook_token="$(env_value "WEBHOOK_STATUS_TOKEN" || true)"
    webhook_signature="$(env_value "WEBHOOK_REQUIRE_SIGNATURE" || true)"

    if [[ -z "${webhook_secret}" ]] || is_placeholder_value "${webhook_secret}"; then
      print_check "FAIL" "WEBHOOK_SHARED_SECRET missing or placeholder"
    else
      print_check "PASS" "WEBHOOK_SHARED_SECRET configured"
    fi

    if [[ -z "${webhook_token}" ]] || is_placeholder_value "${webhook_token}"; then
      print_check "WARN" "WEBHOOK_STATUS_TOKEN missing or placeholder (status endpoint disabled)"
    else
      print_check "PASS" "WEBHOOK_STATUS_TOKEN configured"
    fi

    if [[ "${webhook_signature,,}" == "true" ]]; then
      print_check "PASS" "WEBHOOK_REQUIRE_SIGNATURE=true"
    else
      print_check "WARN" "WEBHOOK_REQUIRE_SIGNATURE should be true"
    fi
  fi

  check_timer_active "homedir-auto-deploy.timer"
  check_service_active "homedir-webhook.service"
  check_service_active "fail2ban.service"
  check_firewall
  check_sshd_baseline

  if [[ -f /etc/nginx/sites-available/homedir.conf ]]; then
    if grep -q "homedir-incident-guard.conf" /etc/nginx/sites-available/homedir.conf; then
      print_check "PASS" "nginx incident guard included"
    else
      print_check "FAIL" "nginx incident guard not included in homedir.conf"
    fi
    if grep -q "homedir-security-hardening.conf" /etc/nginx/sites-available/homedir.conf; then
      print_check "PASS" "nginx security hardening snippet included"
    else
      print_check "WARN" "nginx security hardening snippet missing in homedir.conf"
    fi
  else
    print_check "WARN" "/etc/nginx/sites-available/homedir.conf not found"
  fi

  echo
  echo "audit_summary fails=${AUDIT_FAILS} warns=${AUDIT_WARNS}"
  [[ "${AUDIT_FAILS}" -eq 0 ]]
}

write_sysctl_baseline() {
  run_cmd mkdir -p "$(dirname "${SYSCTL_FILE}")"
  if [[ "${DRY_RUN}" == "true" ]]; then
    cat <<EOF
DRY-RUN: write ${SYSCTL_FILE}
net.ipv4.tcp_syncookies=1
net.ipv4.conf.all.rp_filter=1
net.ipv4.conf.default.rp_filter=1
net.ipv4.icmp_echo_ignore_broadcasts=1
net.ipv4.conf.all.accept_source_route=0
net.ipv4.conf.default.accept_source_route=0
net.ipv4.conf.all.accept_redirects=0
net.ipv4.conf.default.accept_redirects=0
net.ipv4.conf.all.send_redirects=0
net.ipv4.conf.default.send_redirects=0
net.ipv4.tcp_timestamps=0
EOF
  else
    cat > "${SYSCTL_FILE}" <<'EOF'
net.ipv4.tcp_syncookies=1
net.ipv4.conf.all.rp_filter=1
net.ipv4.conf.default.rp_filter=1
net.ipv4.icmp_echo_ignore_broadcasts=1
net.ipv4.conf.all.accept_source_route=0
net.ipv4.conf.default.accept_source_route=0
net.ipv4.conf.all.accept_redirects=0
net.ipv4.conf.default.accept_redirects=0
net.ipv4.conf.all.send_redirects=0
net.ipv4.conf.default.send_redirects=0
net.ipv4.tcp_timestamps=0
EOF
  fi
}

run_apply() {
  [[ "${EUID}" -eq 0 ]] || fail "run apply as root"
  need_cmd chmod
  need_cmd chown
  need_cmd mkdir
  need_cmd systemctl

  if [[ -f "${ENV_FILE}" ]]; then
    run_cmd chown root:root "${ENV_FILE}"
    run_cmd chmod 600 "${ENV_FILE}"
  else
    log "WARNING: ${ENV_FILE} not found; skipping permission hardening"
  fi

  run_cmd mkdir -p "${INCIDENT_LOG_DIR}" "${BACKUP_DIR}"
  run_cmd chown root:root "${INCIDENT_LOG_DIR}" "${BACKUP_DIR}"
  run_cmd chmod 700 "${INCIDENT_LOG_DIR}" "${BACKUP_DIR}"

  if [[ -f "${INCIDENT_LOCK_FILE}" ]]; then
    run_cmd chown root:root "${INCIDENT_LOCK_FILE}"
    run_cmd chmod 600 "${INCIDENT_LOCK_FILE}"
  fi

  write_sysctl_baseline
  if command -v sysctl >/dev/null 2>&1; then
    if [[ "${DRY_RUN}" == "true" ]]; then
      echo "DRY-RUN: sysctl --system"
    else
      sysctl --system >/dev/null || log "WARNING: sysctl --system returned non-zero"
    fi
  fi

  if unit_exists "fail2ban.service"; then
    run_cmd systemctl enable --now fail2ban.service
  fi
  run_cmd systemctl daemon-reload

  if unit_exists "homedir-webhook.service"; then
    if systemctl is-enabled --quiet homedir-webhook.service; then
      run_cmd systemctl restart homedir-webhook.service
    fi
  fi

  AUDIT_FAILS=0
  AUDIT_WARNS=0
  run_audit || fail "hardening apply finished with audit failures"
}

COMMAND="${1:-}"
if [[ -z "${COMMAND}" || "${COMMAND}" == "-h" || "${COMMAND}" == "--help" ]]; then
  usage
  exit 0
fi
shift || true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --incident-log-dir)
      INCIDENT_LOG_DIR="${2:-}"
      shift 2
      ;;
    --backup-dir)
      BACKUP_DIR="${2:-}"
      shift 2
      ;;
    --sysctl-file)
      SYSCTL_FILE="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

case "${COMMAND}" in
  audit)
    run_audit
    ;;
  apply)
    run_apply
    ;;
  *)
    fail "unknown command: ${COMMAND}"
    ;;
esac
