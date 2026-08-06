#!/bin/bash
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

# Extraer voluntarios del evento y convertir a CSV
jq -r --arg eventId "$EVENT_ID" '
  to_entries[] | 
  select(.value.eventId == $eventId) | 
  .value | 
  [
    .id,
    .applicantId,
    .applicantName,
    .email // "N/A",
    .status,
    .aboutMe,
    .joinReason,
    .differentiator // "",
    .rating // "",
    .reviewNotes // "",
    .createdAt,
    .updatedAt
  ] | 
  @csv
' "$VOLUNTEER_FILE" > "$OUTPUT_FILE"

# Agregar encabezado
sed -i '1i"ID","User ID","Nombre","Email","Estado","Sobre mí","Razón para unirse","Diferenciador","Rating","Notas de revisión","Fecha creación","Fecha actualización"' "$OUTPUT_FILE"

VOLUNTEER_COUNT=$(tail -n +2 "$OUTPUT_FILE" | wc -l)
echo ""
echo "✅ Exportación completada"
echo "   Voluntarios encontrados: $VOLUNTEER_COUNT"
echo "   Archivo generado: $OUTPUT_FILE"
echo ""
echo "Para descargar el archivo desde el VPS:"
echo "   scp user@vps:$(pwd)/$OUTPUT_FILE ."
