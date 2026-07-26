#!/bin/bash
set -euo pipefail

# Script para limpiar la agenda corrupta de DevOpsDays Santiago 2026
# y forzar re-seeding desde el código

cd "$(dirname "$0")"

EVENT_ID="20260711152229"
EVENTS_FILE="quarkus-app/data/events.json"
BACKUP_FILE="quarkus-app/data/events.json.backup.$(date +%Y%m%d_%H%M%S)"

echo "=== Fix DevOpsDays Santiago 2026 Agenda ==="
echo ""

# Backup
cp "$EVENTS_FILE" "$BACKUP_FILE"
echo "✅ Backup creado: $BACKUP_FILE"
echo ""

# Validate event exists before modifying
jq -e --arg id "$EVENT_ID" 'has($id)' "$EVENTS_FILE" > /dev/null || {
  echo "❌ Event $EVENT_ID not found in $EVENTS_FILE"
  exit 1
}

# Limpiar agenda del evento devopsdays (ID: 20260711152229)
# Esto forzará el re-seeding automático al reiniciar la app
jq --arg id "$EVENT_ID" '(.[$id] | .agenda) = []' "$EVENTS_FILE" > "${EVENTS_FILE}.tmp" && mv "${EVENTS_FILE}.tmp" "$EVENTS_FILE"

echo "✅ Agenda limpiada (forzará re-seeding)"
echo ""
echo "La agenda se regenerará automáticamente al reiniciar la aplicación"
echo "con el seeding definido en EventService.ensureDevOpsDaysDraftAgenda()"
echo ""
echo "Para aplicar los cambios:"
echo "1. Commit y push este cambio"
echo "2. El CI/CD reiniciará la app"
echo "3. La agenda se regenerará limpia (sin el break de 08:30)"
