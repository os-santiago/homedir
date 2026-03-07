#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${INSIGHTS_INGEST_BASE_URL:-}"
INGEST_KEY="${INSIGHTS_INGEST_KEY:-}"

if [[ -z "${BASE_URL}" || -z "${INGEST_KEY}" ]]; then
  echo "[insights] skip: missing INSIGHTS_INGEST_BASE_URL or INSIGHTS_INGEST_KEY"
  exit 0
fi

if [[ $# -lt 1 ]]; then
  echo "[insights] usage: insights_ingest.sh <start|event> ..."
  exit 0
fi

MODE="$1"
shift

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/ }"
  value="${value//$'\r'/ }"
  echo "$value"
}

build_metadata_json() {
  local first="true"
  local json="{"
  local pair key value escaped_key escaped_value
  for pair in "$@"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    if [[ -z "$key" || "$pair" == "$key" ]]; then
      continue
    fi
    escaped_key="$(json_escape "$key")"
    escaped_value="$(json_escape "$value")"
    if [[ "$first" == "true" ]]; then
      first="false"
    else
      json+=","
    fi
    json+="\"${escaped_key}\":\"${escaped_value}\""
  done
  json+="}"
  echo "$json"
}

post_payload() {
  local endpoint="$1"
  local payload="$2"
  local response_file
  response_file="$(mktemp)"
  local code
  code="$(curl -sS -m 10 -o "$response_file" -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "X-Insights-Key: ${INGEST_KEY}" \
    -d "$payload" \
    "${BASE_URL%/}${endpoint}" || echo "000")"

  if [[ "$code" =~ ^2[0-9][0-9]$ ]]; then
    echo "[insights] ok: ${endpoint} (${code})"
    rm -f "$response_file"
    return 0
  fi

  local body
  body="$(cat "$response_file" 2>/dev/null || true)"
  echo "[insights] warn: ${endpoint} failed (${code}) body=${body}"
  rm -f "$response_file"
  return 0
}

case "$MODE" in
  start)
    if [[ $# -lt 3 ]]; then
      echo "[insights] usage start: insights_ingest.sh start <initiative_id> <title> <definition_started_at> [k=v ...]"
      exit 0
    fi
    initiative_id="$1"
    title="$2"
    definition_started_at="$3"
    shift 3
    metadata_json="$(build_metadata_json "$@")"
    payload="{\"initiativeId\":\"$(json_escape "$initiative_id")\",\"title\":\"$(json_escape "$title")\",\"definitionStartedAt\":\"$(json_escape "$definition_started_at")\",\"metadata\":${metadata_json}}"
    post_payload "/api/internal/insights/initiatives/start" "$payload"
    ;;
  event)
    if [[ $# -lt 2 ]]; then
      echo "[insights] usage event: insights_ingest.sh event <initiative_id> <type> [k=v ...]"
      exit 0
    fi
    initiative_id="$1"
    event_type="$2"
    shift 2
    metadata_json="$(build_metadata_json "$@")"
    payload="{\"initiativeId\":\"$(json_escape "$initiative_id")\",\"type\":\"$(json_escape "$event_type")\",\"metadata\":${metadata_json}}"
    post_payload "/api/internal/insights/events" "$payload"
    ;;
  *)
    echo "[insights] unknown mode: ${MODE}"
    ;;
esac

exit 0
