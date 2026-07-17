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

# Prepare policy fields
POLICY_DRIVEN="false"
POLICY_REFERENCE="null"
POLICY_VERSION="null"

if [[ -n "${POLICY_REF}" ]]; then
  POLICY_DRIVEN="true"
  POLICY_REFERENCE="\"${POLICY_REF}\""
  POLICY_VERSION="\"1.0\""
fi

cat > "${DECISION_FILE}" <<EOF
{
  "id": "${DECISION_ID}",
  "issueNumber": ${ISSUE_NUMBER},
  "prNumber": ${PR_NUMBER:-null},
  "category": "${CATEGORY}",
  "decision": "${DECISION}",
  "rationale": "${RATIONALE}",
  "pattern": "${PATTERN}",
  "reversibility": "${REVERSIBILITY}",
  "confidence": "${CONFIDENCE}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "needsReview": $(if [[ "${CONFIDENCE}" == "LOW" ]] || [[ "${REVERSIBILITY}" =~ [Nn]o ]] || [[ "${CATEGORY}" == "SECURITY" ]]; then echo "true"; else echo "false"; fi),
  "policyDriven": ${POLICY_DRIVEN},
  "policyReference": ${POLICY_REFERENCE},
  "policyVersion": ${POLICY_VERSION},
  "metadata": {
    "worker": "homedir-sdlc-worker",
    "workerVersion": "${HOMEDIR_SDLC_WORKER_VERSION:-unknown}"
  }
}
EOF

echo "${DECISION_FILE}"
