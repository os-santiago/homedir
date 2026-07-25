#!/bin/bash
#
# Script to update DevOpsDays Santiago 2026 agenda via localhost-admin API
#
# Usage:
#   export LOCALHOST_ADMIN_TOKEN="your-token-here"
#   ./update-devopsdays-agenda.sh
#
# This script:
# 1. Creates all required speakers via bulk API
# 2. Updates the event agenda with all 43 sessions
# 3. Validates the update was successful
#

set -e  # Exit on error

# Configuration
API_BASE="http://localhost:8080/api/localhost-admin"
EVENT_ID="devopsdays-santiago-2026"

# Check token
if [ -z "$LOCALHOST_ADMIN_TOKEN" ]; then
  echo "ERROR: LOCALHOST_ADMIN_TOKEN environment variable is not set"
  echo "Usage: export LOCALHOST_ADMIN_TOKEN='your-token-here'"
  exit 1
fi

# Helper function for API calls
api_call() {
  local method=$1
  local endpoint=$2
  local data=$3

  if [ -z "$data" ]; then
    curl -s -X "$method" \
      -H "Authorization: Bearer $LOCALHOST_ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      "$API_BASE$endpoint"
  else
    curl -s -X "$method" \
      -H "Authorization: Bearer $LOCALHOST_ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$data" \
      "$API_BASE$endpoint"
  fi
}

echo "========================================="
echo "DevOpsDays Santiago 2026 - Agenda Update"
echo "========================================="
echo ""

# Step 1: Verify API access
echo "[1/4] Verifying API access..."
STATUS=$(api_call GET "/status")
if echo "$STATUS" | jq -e '.authenticated == true' > /dev/null 2>&1; then
  echo "✓ API access verified"
else
  echo "✗ API authentication failed"
  echo "$STATUS"
  exit 1
fi
echo ""

# Step 2: Create speakers
echo "[2/4] Creating speakers..."
SPEAKERS_TMP="$(mktemp)"
trap 'rm -f "$SPEAKERS_TMP"' EXIT
cat > "$SPEAKERS_TMP" << 'EOF'
{
  "speakers": [
    {
      "id": "caio-medeiros",
      "name": "Caio Medeiros Pinto",
      "bio": "Comité Organizador DevOpsDays Santiago"
    },
    {
      "id": "karen-quijada",
      "name": "Karen Quijada",
      "bio": "Comité Organizador DevOpsDays Santiago"
    },
    {
      "id": "sergio-canales",
      "name": "Sergio Canales",
      "bio": "Comité Organizador DevOpsDays Santiago"
    },
    {
      "id": "jorge-valenzuela",
      "name": "Jorge Valenzuela",
      "bio": "Comité Organizador DevOpsDays Santiago"
    },
    {
      "id": "juan-jose-mendez",
      "name": "Juan José Méndez",
      "bio": "Comité Organizador DevOpsDays Santiago"
    },
    {
      "id": "matias-sonnleitner",
      "name": "Matías Sonnleitner",
      "bio": "Keynote Speaker"
    },
    {
      "id": "andres-barrientos",
      "name": "Andres Barrientos",
      "bio": "Keynote Speaker"
    },
    {
      "id": "johan-prieto",
      "name": "Johan Prieto",
      "bio": "Keynote Speaker"
    },
    {
      "id": "fabrizio-sgura",
      "name": "Fabrizio Sgura",
      "bio": "Keynote Speaker"
    },
    {
      "id": "leonardo-ramirez",
      "name": "Leonardo Ramirez",
      "bio": "TG Native"
    },
    {
      "id": "christian-onetto",
      "name": "Christian Onetto",
      "bio": "Axmos + Datadog"
    },
    {
      "id": "camilla-martins",
      "name": "Camilla Martins",
      "bio": "CFP Speaker & Comité Organizador"
    },
    {
      "id": "axel-labruna",
      "name": "Axel Labruna",
      "bio": "CFP Speaker & Comité Organizador"
    },
    {
      "id": "alvaro-navarro",
      "name": "Alvaro Nicolas Navarro Castro",
      "bio": "CFP Speaker"
    },
    {
      "id": "felipe-carvajal",
      "name": "Felipe Carvajal Brown",
      "bio": "CFP Speaker"
    },
    {
      "id": "gabriel-grobier",
      "name": "Gabriel Grobier",
      "bio": "CFP Speaker"
    },
    {
      "id": "xavier-llauca",
      "name": "Xavier Llauca",
      "bio": "CFP Speaker"
    },
    {
      "id": "cesar-lorca",
      "name": "César Lorca Bacian",
      "bio": "SUSE"
    },
    {
      "id": "carlos-estay",
      "name": "Carlos Estay González",
      "bio": "CFP Speaker"
    },
    {
      "id": "melisa-arenas",
      "name": "Melisa Arenas",
      "bio": "Axmos + Datadog"
    },
    {
      "id": "alonso-utreras",
      "name": "Alonso Utreras",
      "bio": "CFP Speaker"
    },
    {
      "id": "andre-fellipe",
      "name": "André Fellipe",
      "bio": "CFP Speaker"
    },
    {
      "id": "francisco-meneses",
      "name": "Francisco Meneses",
      "bio": "Red Hat & CFP Speaker"
    },
    {
      "id": "francisco-raposo",
      "name": "Francisco Raposo",
      "bio": "Red Hat"
    },
    {
      "id": "boris-quiroz",
      "name": "Boris Quiroz",
      "bio": "CFP Speaker"
    },
    {
      "id": "luis-santiago-segura",
      "name": "Luis Santiago Segura Peláez",
      "bio": "CFP Speaker"
    },
    {
      "id": "meraioth-ulloa",
      "name": "Meraioth Ulloa Salazar",
      "bio": "Buk & CFP Speaker"
    },
    {
      "id": "victor-recio",
      "name": "Victor S. Recio",
      "bio": "CFP Speaker"
    },
    {
      "id": "josua-castro",
      "name": "Josua Castro Vicente",
      "bio": "CFP Speaker"
    },
    {
      "id": "pilar-rebolledo",
      "name": "Pilar Rebolledo",
      "bio": "CFP Speaker"
    }
  ]
}
EOF

BULK_RESULT=$(api_call POST "/speakers/bulk" "@$SPEAKERS_TMP")
CREATED_COUNT=$(echo "$BULK_RESULT" | jq -r '.createdCount')
ERROR_COUNT=$(echo "$BULK_RESULT" | jq -r '.errorCount')

echo "✓ Created $CREATED_COUNT speakers"
if [ "$ERROR_COUNT" -gt 0 ]; then
  echo "⚠ $ERROR_COUNT errors occurred:"
  echo "$BULK_RESULT" | jq -r '.errors[]'
fi
echo ""

# Step 3: Update agenda
echo "[3/4] Updating event agenda..."
echo "   Loading agenda JSON from devopsdays-santiago-2026-agenda.json..."

if [ ! -f "devopsdays-santiago-2026-agenda.json" ]; then
  echo "✗ File devopsdays-santiago-2026-agenda.json not found"
  echo "   Please run this script from the homedir root directory"
  exit 1
fi

# Wrap agenda array in object
AGENDA_DATA=$(jq '{agenda: .}' devopsdays-santiago-2026-agenda.json)

AGENDA_RESULT=$(api_call PUT "/events/$EVENT_ID/agenda" "$AGENDA_DATA")

if echo "$AGENDA_RESULT" | jq -e '.error' > /dev/null 2>&1; then
  echo "✗ Failed to update agenda:"
  echo "$AGENDA_RESULT" | jq '.'
  exit 1
fi

AGENDA_COUNT=$(echo "$AGENDA_RESULT" | jq -r '.agendaCount')
echo "✓ Updated agenda with $AGENDA_COUNT sessions"
echo ""

# Step 4: Verify
echo "[4/4] Verifying update..."
EVENT=$(api_call GET "/events")
EVENT_AGENDA_COUNT=$(echo "$EVENT" | jq -r --arg id "$EVENT_ID" '.[] | select(.id == $id) | .agenda | length')

if [ "$EVENT_AGENDA_COUNT" -eq "$AGENDA_COUNT" ]; then
  echo "✓ Verification successful: Event has $EVENT_AGENDA_COUNT sessions in agenda"
else
  echo "⚠ Mismatch: Expected $AGENDA_COUNT sessions, found $EVENT_AGENDA_COUNT"
fi
echo ""

echo "========================================="
echo "✓ Agenda update completed successfully!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Verify event at: https://homedir.opensourcesantiago.io/event/$EVENT_ID"
echo "2. Check speaker profiles are displaying correctly"
echo "3. Validate session times and locations"
echo ""
