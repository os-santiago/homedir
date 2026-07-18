#!/bin/bash
# SDLC E2E Dashboard Monitor
# Real-time monitoring of SDLC dashboard metrics during E2E tests

set -euo pipefail

# Configuration
DASHBOARD_URL="${SDLC_DASHBOARD_URL:-http://localhost:8080}"
API_BASE="${DASHBOARD_URL}/api/sdlc"
REFRESH_INTERVAL="${E2E_DASHBOARD_REFRESH:-5}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# State
WATCH_ISSUE=""
WATCH_PR=""

clear_screen() {
  printf '\033[2J\033[H'
}

draw_header() {
  local timestamp
  timestamp=$(date '+%Y-%m-%d %H:%M:%S')

  echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║${BOLD}                    SDLC E2E DASHBOARD MONITOR                              ${NC}${BLUE}║${NC}"
  echo -e "${BLUE}╠════════════════════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${BLUE}║${NC} Updated: ${timestamp}                                            ${BLUE}║${NC}"
  if [[ -n "${WATCH_ISSUE}" ]]; then
    printf "${BLUE}║${NC} Watching: Issue #%-4s " "${WATCH_ISSUE}"
    if [[ -n "${WATCH_PR}" ]]; then
      printf "| PR #%-4s                                          ${BLUE}║${NC}\n" "${WATCH_PR}"
    else
      printf "                                                   ${BLUE}║${NC}\n"
    fi
  fi
  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
  echo ""
}

get_health_color() {
  local status="$1"
  case "${status,,}" in
    running|healthy|ok|idle)
      echo "${GREEN}"
      ;;
    paused|warning|unknown)
      echo "${YELLOW}"
      ;;
    error|critical|failed)
      echo "${RED}"
      ;;
    *)
      echo "${NC}"
      ;;
  esac
}

draw_worker_status() {
  local status_json
  status_json=$(curl -s "${API_BASE}/status" 2>/dev/null || echo '{}')

  local worker_state
  worker_state=$(echo "${status_json}" | jq -r '.worker.state // "unknown"')

  local heartbeat_age
  heartbeat_age=$(echo "${status_json}" | jq -r '.worker.heartbeatAge // 0')

  local detail
  detail=$(echo "${status_json}" | jq -r '.worker.detail // "No data"')

  local color
  color=$(get_health_color "${worker_state}")

  echo -e "${CYAN}┌─ Worker Status ────────────────────────────────────────────────────────────┐${NC}"
  printf "${CYAN}│${NC} State: ${color}%-15s${NC} " "${worker_state^^}"
  printf "Heartbeat Age: %-8s " "${heartbeat_age}s"
  echo -e "${CYAN}│${NC}"
  printf "${CYAN}│${NC} Detail: %-68s ${CYAN}│${NC}\n" "${detail:0:68}"
  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_pipeline_summary() {
  local pipeline_json
  pipeline_json=$(curl -s "${API_BASE}/pipeline" 2>/dev/null || echo '[]')

  echo -e "${CYAN}┌─ Pipeline Summary ─────────────────────────────────────────────────────────┐${NC}"
  printf "${CYAN}│${NC} %-20s %8s %12s %10s ${CYAN}│${NC}\n" "STAGE" "COUNT" "AVG TIME" "ANOMALIES"
  echo -e "${CYAN}├────────────────────────────────────────────────────────────────────────────┤${NC}"

  echo "${pipeline_json}" | jq -r '.[] | "\(.name)|\(.count)|\(.avgDuration)|\(.anomalies)"' | while IFS='|' read -r name count avg_dur anomalies; do
    local count_color="${NC}"
    if [[ ${count} -gt 0 ]]; then
      count_color="${GREEN}"
    fi

    local anomaly_color="${NC}"
    if [[ ${anomalies} -gt 0 ]]; then
      anomaly_color="${YELLOW}"
    fi

    printf "${CYAN}│${NC} %-20s ${count_color}%8s${NC} %12ss ${anomaly_color}%10s${NC} ${CYAN}│${NC}\n" \
      "${name:0:20}" "${count}" "${avg_dur}" "${anomalies}"
  done

  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_active_issues() {
  local issues_json
  issues_json=$(curl -s "${API_BASE}/issues" 2>/dev/null || echo '[]')

  local total
  total=$(echo "${issues_json}" | jq 'length')

  echo -e "${CYAN}┌─ Active Issues (${total}) ──────────────────────────────────────────────────────┐${NC}"

  if [[ ${total} -eq 0 ]]; then
    echo -e "${CYAN}│${NC} No active issues                                                           ${CYAN}│${NC}"
  else
    printf "${CYAN}│${NC} %-6s %-30s %-15s %10s ${CYAN}│${NC}\n" "NUMBER" "TITLE" "STATE" "AGE"
    echo -e "${CYAN}├────────────────────────────────────────────────────────────────────────────┤${NC}"

    echo "${issues_json}" | jq -r '.[:5] | .[] | "\(.number)|\(.title)|\(.state)|\(.ageSeconds)"' | while IFS='|' read -r number title state age; do
      local highlight=""
      if [[ -n "${WATCH_ISSUE}" ]] && [[ "${number}" == "${WATCH_ISSUE}" ]]; then
        highlight="${BOLD}${YELLOW}"
      fi

      local state_color
      state_color=$(get_health_color "${state}")

      printf "${CYAN}│${NC} ${highlight}%-6s %-30s ${state_color}%-15s${NC} %10ss ${NC}${CYAN}│${NC}\n" \
        "#${number}" "${title:0:30}" "${state}" "${age}"
    done
  fi

  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_active_prs() {
  local prs_json
  prs_json=$(curl -s "${API_BASE}/prs" 2>/dev/null || echo '[]')

  local total
  total=$(echo "${prs_json}" | jq 'length')

  echo -e "${CYAN}┌─ Active PRs (${total}) ────────────────────────────────────────────────────────┐${NC}"

  if [[ ${total} -eq 0 ]]; then
    echo -e "${CYAN}│${NC} No active PRs                                                              ${CYAN}│${NC}"
  else
    printf "${CYAN}│${NC} %-6s %-30s %-15s %10s ${CYAN}│${NC}\n" "NUMBER" "TITLE" "CHECKS" "AGE"
    echo -e "${CYAN}├────────────────────────────────────────────────────────────────────────────┤${NC}"

    echo "${prs_json}" | jq -r '.[:5] | .[] | "\(.number)|\(.title)|\(.checksStatus // \"unknown\")|\(.ageSeconds)"' | while IFS='|' read -r number title checks age; do
      local highlight=""
      if [[ -n "${WATCH_PR}" ]] && [[ "${number}" == "${WATCH_PR}" ]]; then
        highlight="${BOLD}${YELLOW}"
      fi

      local checks_color
      checks_color=$(get_health_color "${checks}")

      printf "${CYAN}│${NC} ${highlight}%-6s %-30s ${checks_color}%-15s${NC} %10ss ${NC}${CYAN}│${NC}\n" \
        "#${number}" "${title:0:30}" "${checks}" "${age}"
    done
  fi

  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_metrics() {
  local metrics_json
  metrics_json=$(curl -s "${API_BASE}/metrics?days=7" 2>/dev/null || echo '{}')

  local autonomy
  autonomy=$(echo "${metrics_json}" | jq -r '.autonomy // 0')

  local auto_merge
  auto_merge=$(echo "${metrics_json}" | jq -r '.autoMerge // 0')

  local daily_throughput
  daily_throughput=$(echo "${metrics_json}" | jq -r '.throughput.daily // 0')

  local weekly_throughput
  weekly_throughput=$(echo "${metrics_json}" | jq -r '.throughput.weekly // 0')

  echo -e "${CYAN}┌─ Metrics (7 days) ─────────────────────────────────────────────────────────┐${NC}"
  printf "${CYAN}│${NC} Autonomy: ${GREEN}%6.1f%%${NC}  " "${autonomy}"
  printf "Auto-Merge: ${GREEN}%6.1f%%${NC}  " "${auto_merge}"
  printf "Throughput: %3s/day (%3s/week) ${CYAN}│${NC}\n" "${daily_throughput}" "${weekly_throughput}"
  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_anomalies() {
  local anomalies_json
  anomalies_json=$(curl -s "${API_BASE}/anomalies" 2>/dev/null || echo '[]')

  local total
  total=$(echo "${anomalies_json}" | jq 'length')

  echo -e "${CYAN}┌─ Anomalies (${total}) ─────────────────────────────────────────────────────────┐${NC}"

  if [[ ${total} -eq 0 ]]; then
    echo -e "${CYAN}│${NC} ${GREEN}✓${NC} No anomalies detected                                                  ${CYAN}│${NC}"
  else
    echo "${anomalies_json}" | jq -r '.[:3] | .[] | "\(.severity)|\(.description)"' | while IFS='|' read -r severity desc; do
      local color="${YELLOW}"
      if [[ "${severity}" == "critical" ]]; then
        color="${RED}"
      fi

      printf "${CYAN}│${NC} ${color}%-10s${NC} %-63s ${CYAN}│${NC}\n" "${severity^^}" "${desc:0:63}"
    done

    if [[ ${total} -gt 3 ]]; then
      printf "${CYAN}│${NC} ... and %d more                                                            ${CYAN}│${NC}\n" $((total - 3))
    fi
  fi

  echo -e "${CYAN}└────────────────────────────────────────────────────────────────────────────┘${NC}"
  echo ""
}

draw_footer() {
  echo -e "${BLUE}Press Ctrl+C to exit | Refresh every ${REFRESH_INTERVAL}s${NC}"
}

draw_dashboard() {
  clear_screen
  draw_header
  draw_worker_status
  draw_pipeline_summary
  draw_active_issues
  draw_active_prs
  draw_metrics
  draw_anomalies
  draw_footer
}

watch_issue() {
  local issue="$1"
  local pr="${2:-}"

  WATCH_ISSUE="${issue}"
  WATCH_PR="${pr}"

  while true; do
    draw_dashboard
    sleep "${REFRESH_INTERVAL}"
  done
}

show_snapshot() {
  local snapshot_json
  snapshot_json=$(curl -s "${API_BASE}/snapshot" 2>/dev/null || echo '{}')

  echo "${snapshot_json}" | jq '.'
}

check_api_health() {
  local health_url="${DASHBOARD_URL}/q/health"

  echo "Checking API health at ${health_url}..."

  if curl -sf "${health_url}" >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} API is healthy"
    return 0
  else
    echo -e "${RED}✗${NC} API is not responding"
    return 1
  fi
}

show_usage() {
  cat <<EOF
Usage: $0 [OPTIONS] [COMMAND] [ARGS]

Commands:
  watch [issue] [pr]  - Watch dashboard with optional issue/PR highlighting
  snapshot            - Dump raw JSON snapshot
  health              - Check API health

Options:
  --url URL           - Dashboard URL (default: ${DASHBOARD_URL})
  --refresh SECONDS   - Refresh interval (default: ${REFRESH_INTERVAL})
  --help              - Show this help

Examples:
  # Watch dashboard
  $0 watch

  # Watch dashboard highlighting issue #1234
  $0 watch 1234

  # Watch dashboard highlighting issue #1234 and PR #567
  $0 watch 1234 567

  # Check API health
  $0 health

  # Custom dashboard URL
  $0 --url https://sdlc.example.com watch

EOF
}

main() {
  local command="watch"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --url)
        DASHBOARD_URL="$2"
        API_BASE="${DASHBOARD_URL}/api/sdlc"
        shift 2
        ;;
      --refresh)
        REFRESH_INTERVAL="$2"
        shift 2
        ;;
      --help)
        show_usage
        exit 0
        ;;
      watch|snapshot|health)
        command="$1"
        shift
        break
        ;;
      *)
        echo "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  # Validate dependencies
  if ! command -v curl &>/dev/null; then
    echo "Error: curl is required"
    exit 1
  fi

  if ! command -v jq &>/dev/null; then
    echo "Error: jq is required"
    exit 1
  fi

  case "${command}" in
    watch)
      local issue="${1:-}"
      local pr="${2:-}"
      watch_issue "${issue}" "${pr}"
      ;;
    snapshot)
      show_snapshot
      ;;
    health)
      check_api_health
      ;;
    *)
      echo "Unknown command: ${command}"
      show_usage
      exit 1
      ;;
  esac
}

main "$@"
