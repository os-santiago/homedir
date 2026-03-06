#!/usr/bin/env bash
set -euo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
  set +a
fi

CFP_MONITOR_ENABLED="${CFP_MONITOR_ENABLED:-true}"
CFP_MONITOR_WINDOW_MINUTES="${CFP_MONITOR_WINDOW_MINUTES:-5}"
CFP_MONITOR_LINES_PER_MINUTE="${CFP_MONITOR_LINES_PER_MINUTE:-400}"
CFP_MONITOR_MIN_SAMPLE="${CFP_MONITOR_MIN_SAMPLE:-30}"
CFP_MONITOR_MAX_ERROR_RATE_PERCENT="${CFP_MONITOR_MAX_ERROR_RATE_PERCENT:-5}"
CFP_MONITOR_MAX_429_COUNT="${CFP_MONITOR_MAX_429_COUNT:-20}"
CFP_MONITOR_MAX_TIMEOUT_COUNT="${CFP_MONITOR_MAX_TIMEOUT_COUNT:-5}"
CFP_MONITOR_ACCESS_LOG="${CFP_MONITOR_ACCESS_LOG:-/var/log/nginx/access.log}"
CFP_MONITOR_ERROR_LOG="${CFP_MONITOR_ERROR_LOG:-/var/log/nginx/error.log}"
CFP_MONITOR_LOGFILE="${CFP_MONITOR_LOGFILE:-/var/log/homedir-cfp-monitor.log}"
ALERT_SCRIPT="${ALERT_SCRIPT:-/usr/local/bin/homedir-discord-alert.sh}"

MODE="check"

usage() {
  cat <<'EOF'
Usage:
  homedir-cfp-traffic-guard.sh [status|check]

Description:
  Observes CFP/community critical routes in nginx logs and raises alerts on
  high error rate, 429 spikes, or timeout spikes.

Modes:
  status  Print metrics and always exit 0.
  check   Print metrics and exit non-zero when thresholds are breached.

Configuration (from /etc/homedir.env by default):
  CFP_MONITOR_ENABLED=true
  CFP_MONITOR_WINDOW_MINUTES=5
  CFP_MONITOR_LINES_PER_MINUTE=400
  CFP_MONITOR_MIN_SAMPLE=30
  CFP_MONITOR_MAX_ERROR_RATE_PERCENT=5
  CFP_MONITOR_MAX_429_COUNT=20
  CFP_MONITOR_MAX_TIMEOUT_COUNT=5
  CFP_MONITOR_ACCESS_LOG=/var/log/nginx/access.log
  CFP_MONITOR_ERROR_LOG=/var/log/nginx/error.log
  CFP_MONITOR_LOGFILE=/var/log/homedir-cfp-monitor.log
EOF
}

log() {
  local line
  line="$(date -u +%Y-%m-%dT%H:%M:%SZ) $*"
  echo "${line}" | tee -a "${CFP_MONITOR_LOGFILE}" >/dev/null
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

need_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

notify_alert() {
  local severity="$1"
  local title="$2"
  local message="$3"
  local details="${4:-}"
  if [[ -x "${ALERT_SCRIPT}" ]]; then
    "${ALERT_SCRIPT}" "${severity}" "${title}" "${message}" "${details}" >/dev/null 2>&1 || true
  fi
}

to_int() {
  local raw="$1"
  if [[ "${raw}" =~ ^[0-9]+$ ]]; then
    echo "${raw}"
  else
    echo "0"
  fi
}

main() {
  local arg="${1:-check}"
  case "${arg}" in
    status|check)
      MODE="${arg}"
      ;;
    -h|--help|help)
      usage
      exit 0
      ;;
    *)
      fail "unknown mode: ${arg}"
      ;;
  esac

  need_cmd awk
  need_cmd tail

  if [[ "${CFP_MONITOR_ENABLED,,}" != "true" ]]; then
    log "cfp_monitor disabled"
    exit 0
  fi

  [[ -f "${CFP_MONITOR_ACCESS_LOG}" ]] || fail "access log not found: ${CFP_MONITOR_ACCESS_LOG}"
  [[ -f "${CFP_MONITOR_ERROR_LOG}" ]] || fail "error log not found: ${CFP_MONITOR_ERROR_LOG}"

  local window lines
  window="$(to_int "${CFP_MONITOR_WINDOW_MINUTES}")"
  (( window > 0 )) || window=5
  lines="$(to_int "${CFP_MONITOR_LINES_PER_MINUTE}")"
  (( lines > 0 )) || lines=400

  local access_tail_lines error_tail_lines
  access_tail_lines=$(( window * lines ))
  error_tail_lines=$(( window * 120 ))
  (( access_tail_lines < 200 )) && access_tail_lines=200
  (( error_tail_lines < 120 )) && error_tail_lines=120

  local raw_counts
  raw_counts="$(
    tail -n "${access_tail_lines}" "${CFP_MONITOR_ACCESS_LOG}" | awk '
      BEGIN {
        total=0; err=0; s429=0; s5xx=0;
      }
      {
        req=$7;
        if (req == "" || req == "-") next;
        is_cfp = (index(req, "/api/events/") > 0 && index(req, "/cfp/") > 0);
        is_community = (index(req, "/api/community/content") > 0);
        if (!(is_cfp || is_community)) next;
        total++;
        status=$9+0;
        if (status == 429) s429++;
        if (status >= 500 && status <= 599) s5xx++;
        if ((status == 429) || (status >= 500 && status <= 599)) err++;
      }
      END {
        printf "%d %d %d %d\n", total, err, s429, s5xx;
      }
    '
  )"

  local total errors count_429 count_5xx
  read -r total errors count_429 count_5xx <<< "${raw_counts}"

  local timeout_count
  timeout_count="$(
    tail -n "${error_tail_lines}" "${CFP_MONITOR_ERROR_LOG}" | awk '
      BEGIN { c=0; }
      {
        is_relevant = (index($0, "/api/events/") > 0 && index($0, "/cfp/") > 0) || index($0, "/api/community/content") > 0;
        if (!is_relevant) next;
        if (index($0, "timed out") > 0 || index($0, "upstream prematurely closed connection") > 0 || index($0, "Connection reset by peer") > 0) c++;
      }
      END { printf "%d\n", c; }
    '
  )"

  local error_rate
  if (( total > 0 )); then
    error_rate="$(awk -v e="${errors}" -v t="${total}" 'BEGIN { printf "%.2f", (e * 100.0) / t }')"
  else
    error_rate="0.00"
  fi

  local breaches=0
  local reasons=()
  local min_sample max_err_rate max_429 max_timeout
  min_sample="$(to_int "${CFP_MONITOR_MIN_SAMPLE}")"
  (( min_sample > 0 )) || min_sample=30
  max_err_rate="${CFP_MONITOR_MAX_ERROR_RATE_PERCENT}"
  max_429="$(to_int "${CFP_MONITOR_MAX_429_COUNT}")"
  (( max_429 > 0 )) || max_429=20
  max_timeout="$(to_int "${CFP_MONITOR_MAX_TIMEOUT_COUNT}")"
  (( max_timeout > 0 )) || max_timeout=5

  if (( total < min_sample )); then
    reasons+=("sample_below_minimum(${total}<${min_sample})")
  else
    if awk -v a="${error_rate}" -v b="${max_err_rate}" 'BEGIN { exit (a > b ? 0 : 1) }'; then
      breaches=$((breaches + 1))
      reasons+=("error_rate=${error_rate}%>${max_err_rate}%")
    fi
    if (( count_429 > max_429 )); then
      breaches=$((breaches + 1))
      reasons+=("429=${count_429}>${max_429}")
    fi
    if (( timeout_count > max_timeout )); then
      breaches=$((breaches + 1))
      reasons+=("timeouts=${timeout_count}>${max_timeout}")
    fi
  fi

  local summary
  summary="window_min=${window} total=${total} errors=${errors} error_rate=${error_rate}% 429=${count_429} 5xx=${count_5xx} timeouts=${timeout_count}"
  log "cfp_traffic_guard ${summary}"

  if (( breaches > 0 )); then
    local details
    details="${summary} reasons=$(IFS=,; echo "${reasons[*]}")"
    notify_alert "FAIL" "CFP traffic guard breach" "CFP critical route error thresholds exceeded" "${details}"
    if [[ "${MODE}" == "check" ]]; then
      echo "${details}"
      exit 1
    fi
  fi

  echo "${summary}"
  if [[ "${MODE}" == "check" ]]; then
    exit 0
  fi
}

main "$@"
