#!/bin/bash
set -euo pipefail
# Script para exportar voluntarios de un evento específico

EVENT_ID="${1:-devopsdays-santiago-2026}"
DATA_DIR="${2:-/var/lib/homedir}"
OUTPUT_FILE="volunteers-${EVENT_ID}-$(date +%Y%m%d-%H%M%S).csv"

VOLUNTEER_FILE="${DATA_DIR}/volunteer-submissions.json"

if [ ! -f "$VOLUNTEER_FILE" ]; then
  echo "Error: Archivo $VOLUNTEER_FILE no encontrado"
  echo "Uso: $0 <event-id> <data-dir>"
  echo "Ejemplo: $0 devopsdays-santiago-2026 /var/lib/homedir"
  exit 1
fi

echo "Extrayendo voluntarios del evento: $EVENT_ID"
echo "Desde archivo: $VOLUNTEER_FILE"
echo "Salida: $OUTPUT_FILE"
echo ""

TMP_OUTPUT=$(mktemp "${OUTPUT_FILE}.tmp.XXXXXX")
trap 'rm -f "$TMP_OUTPUT"' EXIT

# Extraer voluntarios del evento y convertir a CSV.
# Los nombres de campo coinciden con la persistencia de VolunteerApplication
# (snake_case). Se neutralizan valores que inicien con =, +, - o @ para evitar
# interpretación de fórmulas al abrir el CSV.
jq -r --arg eventId "$EVENT_ID" '
  to_entries[] |
  select(.value.event_id == $eventId) |
  .value |
  [
    (.id // ""),
    (.applicant_user_id // ""),
    (.applicant_name // ""),
    (.email // "N/A"),
    (.status // ""),
    (.about_me // ""),
    (.join_reason // ""),
    (.differentiator // ""),
    (.rating_profile // ""),
    (.moderation_note // ""),
    (.created_at // ""),
    (.updated_at // "")
  ] |
  map(if ((length > 0) and ((.[0:1] == "=") or (.[0:1] == "+") or (.[0:1] == "-") or (.[0:1] == "@"))) then ("'"'"'" + .) else . end)) |
  @csv
' "$VOLUNTEER_FILE" > "$TMP_OUTPUT"

# Agregar encabezado
sed -i '1i"ID","User ID","Nombre","Email","Estado","Sobre mí","Razón para unirse","Diferenciador","Rating","Notas de revisión","Fecha creación","Fecha actualización"' "$TMP_OUTPUT"

mv "$TMP_OUTPUT" "$OUTPUT_FILE"
trap - EXIT

VOLUNTEER_COUNT=$(tail -n +2 "$OUTPUT_FILE" | wc -l)
echo ""
echo "✅ Exportación completada"
echo "   Voluntarios encontrados: $VOLUNTEER_COUNT"
echo "   Archivo generado: $OUTPUT_FILE"
echo ""
echo "Para descargar el archivo desde el VPS:"
echo "   scp user@vps:$(pwd)/$OUTPUT_FILE ."