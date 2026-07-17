#!/usr/bin/env bash
# Log autonomous decision made by AI SDLC worker
# Usage: sdlc-log-autonomous-decision.sh <issue> <category> <decision> <rationale> <pattern> <reversibility> <confidence> [pr_number] [policy_ref]

set -euo pipefail

ISSUE_NUMBER="${1:-}"
CATEGORY="${2:-OTHER}"
DECISION="${3:-}"
RATIONALE="${4:-}"
PATTERN="${5:-}"
REVERSIBILITY="${6:-Yes}"
CONFIDENCE="${7:-MEDIUM}"
PR_NUMBER="${8:-}"
POLICY_REF="${9:-}"  # NEW: Optional policy reference

STATE_DIR="${HOMEDIR_SDLC_STATE_DIR:-/var/lib/homedir-sdlc}"
DECISIONS_DIR="${STATE_DIR}/autonomous-decisions"

if [[ -z "${ISSUE_NUMBER}" ]] || [[ -z "${DECISION}" ]]; then
  echo "Usage: $0 <issue> <category> <decision> <rationale> <pattern> <reversibility> <confidence> [pr_number]" >&2
  exit 1
fi

mkdir -p "${DECISIONS_DIR}"

# Generate decision ID
TIMESTAMP=$(date +%s%3N)
CATEGORY_SLUG=$(echo "${CATEGORY}" | tr '[:upper:]' '[:lower:]' | tr '_' '-')
DECISION_ID="decision-${ISSUE_NUMBER}-${CATEGORY_SLUG}-${TIMESTAMP: -5}"

# Create decision JSON
DECISION_FILE="${DECISIONS_DIR}/${DECISION_ID}.json"

POLICY_DRIVEN="false"
if [[ -n "${POLICY_REF}" ]]; then
  POLICY_DRIVEN="true"
fi

NEEDS_REVIEW="false"
if [[ "${CONFIDENCE}" == "LOW" ]] || [[ "${REVERSIBILITY}" =~ [Nn]o ]] || [[ "${CATEGORY}" == "SECURITY" ]]; then
  NEEDS_REVIEW="true"
fi

jq -n \
  --arg id "${DECISION_ID}" \
  --argjson issueNumber "${ISSUE_NUMBER}" \
  --arg prNumber "${PR_NUMBER}" \
  --arg category "${CATEGORY}" \
  --arg decision "${DECISION}" \
  --arg rationale "${RATIONALE}" \
  --arg pattern "${PATTERN}" \
  --arg reversibility "${REVERSIBILITY}" \
  --arg confidence "${CONFIDENCE}" \
  --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --argjson needsReview ${NEEDS_REVIEW} \
  --argjson policyDriven ${POLICY_DRIVEN} \
  --arg policyReference "${POLICY_REF}" \
  --argjson policyVersion "$(if [[ -n "${POLICY_REF}" ]]; then echo '"1.0"'; else echo 'null'; fi)" \
  --arg workerVersion "${HOMEDIR_SDLC_WORKER_VERSION:-unknown}" \
  '{
    "id": $id,
    "issueNumber": $issueNumber,
    "prNumber": (if $prNumber == "" then null else ($prNumber | tonumber) end),
    "category": $category,
    "decision": $decision,
    "rationale": $rationale,
    "pattern": $pattern,
    "reversibility": $reversibility,
    "confidence": $confidence,
    "timestamp": $timestamp,
    "needsReview": $needsReview,
    "policyDriven": $policyDriven,
    "policyReference": (if $policyReference == "" then null else $policyReference end),
    "policyVersion": $policyVersion,
    "metadata": {
      "worker": "homedir-sdlc-worker",
      "workerVersion": $workerVersion
    }
  }' > "${DECISION_FILE}"

echo "${DECISION_FILE}"
