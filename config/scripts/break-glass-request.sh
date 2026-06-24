#!/usr/bin/env bash
#
# Break-Glass Request Helper Script
# Usage: ./scripts/break-glass-request.sh <incident-number> <severity>
#
# This script assists with creating a structured break-glass request
# following the emergency runbook procedures.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    cat <<EOF
Usage: $0 <incident-number> <severity>

Arguments:
  incident-number  GitHub issue number of the incident (e.g., 1001)
  severity         Incident severity: P0, P1, or P2

Example:
  $0 1001 P0

This script will:
  1. Validate the incident exists
  2. Create a break-glass request from template
  3. Post request to incident channel (if configured)
  4. Track authorization status

EOF
    exit 1
}

# Validate arguments
if [ $# -ne 2 ]; then
    echo -e "${RED}Error: Invalid number of arguments${NC}"
    usage
fi

INCIDENT_NUMBER="$1"
SEVERITY="$2"

# Validate severity
if [[ ! "$SEVERITY" =~ ^P[0-2]$ ]]; then
    echo -e "${RED}Error: Severity must be P0, P1, or P2${NC}"
    usage
fi

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed${NC}"
    echo "Install from: https://cli.github.com/"
    exit 1
fi

# Validate incident exists
echo -e "${BLUE}Validating incident #${INCIDENT_NUMBER}...${NC}"
if ! gh issue view "${INCIDENT_NUMBER}" &> /dev/null; then
    echo -e "${RED}Error: Incident #${INCIDENT_NUMBER} not found${NC}"
    exit 1
fi

# Get incident details
INCIDENT_TITLE=$(gh issue view "${INCIDENT_NUMBER}" --json title --jq '.title')
INCIDENT_URL=$(gh issue view "${INCIDENT_NUMBER}" --json url --jq '.url')

echo -e "${GREEN}Found incident: ${INCIDENT_TITLE}${NC}"
echo -e "${BLUE}URL: ${INCIDENT_URL}${NC}"
echo ""

# Determine required authorizers based on severity
case "$SEVERITY" in
    P0)
        REQUIRED_AUTHORIZERS="1 Tech Lead OR 1 Engineering Manager"
        SLA_MINUTES=15
        ;;
    P1)
        REQUIRED_AUTHORIZERS="1 Tech Lead AND 1 Engineering Manager"
        SLA_MINUTES=30
        ;;
    P2)
        REQUIRED_AUTHORIZERS="2 Tech Leads OR 1 EM + 1 Product Owner"
        SLA_MINUTES=60
        ;;
esac

echo -e "${YELLOW}=== BREAK-GLASS REQUEST ===${NC}"
echo -e "Incident: #${INCIDENT_NUMBER}"
echo -e "Severity: ${SEVERITY}"
echo -e "Required Authorizers: ${REQUIRED_AUTHORIZERS}"
echo -e "Authorization SLA: ${SLA_MINUTES} minutes"
echo ""

# Create break-glass request file
REQUEST_FILE="${PROJECT_ROOT}/docs/governance/requests/break-glass-${INCIDENT_NUMBER}.md"
mkdir -p "$(dirname "${REQUEST_FILE}")"

# Copy template
TEMPLATE_FILE="${PROJECT_ROOT}/docs/governance/templates/break_glass_request.md"
if [ ! -f "${TEMPLATE_FILE}" ]; then
    echo -e "${RED}Error: Template file not found: ${TEMPLATE_FILE}${NC}"
    exit 1
fi

cp "${TEMPLATE_FILE}" "${REQUEST_FILE}"

# Pre-fill some fields
sed -i "s/^- \*\*Incident Number\*\*: #/- **Incident Number**: #${INCIDENT_NUMBER}/" "${REQUEST_FILE}"
sed -i "s/^- \*\*Severity\*\*: \[P0\/P1\/P2\]/- **Severity**: ${SEVERITY}/" "${REQUEST_FILE}"
sed -i "s/^- \*\*Reported At\*\*: /- **Reported At**: $(date -Iseconds)/" "${REQUEST_FILE}"

echo -e "${GREEN}Created break-glass request file: ${REQUEST_FILE}${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Edit the request file to fill in all required details:"
echo "   ${REQUEST_FILE}"
echo ""
echo "2. Complete the following sections:"
echo "   - Impact Assessment"
echo "   - Standard Process Blocker"
echo "   - Proposed Emergency Action"
echo "   - Risk Analysis"
echo "   - Rollback Plan"
echo "   - Monitoring Plan"
echo ""
echo "3. Request authorization by posting to incident channel:"
echo "   🚨 BREAK-GLASS REQUEST 🚨"
echo "   Incident: #${INCIDENT_NUMBER}"
echo "   Severity: ${SEVERITY}"
echo "   Details: [Link to request file or paste key sections]"
echo "   Required: ${REQUIRED_AUTHORIZERS}"
echo ""
echo "4. Document authorizations in the request file"
echo ""
echo "5. Execute break-glass procedure once authorized"
echo ""
echo "6. Log exception in: ${PROJECT_ROOT}/docs/governance/break_glass_exceptions.log"
echo ""

# Offer to open editor
read -p "Open request file in editor now? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    ${EDITOR:-vim} "${REQUEST_FILE}"
fi

echo -e "${GREEN}Break-glass request helper completed${NC}"
