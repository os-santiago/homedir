#!/bin/bash
# Script para extraer voluntarios con emails del evento DevOpsDays Santiago 2026
# Ejecutar en el VPS o con acceso al archivo de datos

EVENT_ID="devopsdays-santiago-2026"
DATA_DIR="${1:-/var/lib/homedir}"
OUTPUT_FILE="volunteers-with-emails-$(date +%Y%m%d-%H%M%S).csv"

VOLUNTEERS_FILE="${DATA_DIR}/volunteer-submissions.json"
PROFILES_FILE="${DATA_DIR}/profiles.json"

echo "=================================================="
echo "Extrayendo Voluntarios - DevOpsDays Santiago 2026"
echo "=================================================="
echo ""

if [ ! -f "$VOLUNTEERS_FILE" ]; then
  echo "❌ Error: Archivo $VOLUNTEERS_FILE no encontrado"
  echo ""
  echo "Uso:"
  echo "  En el VPS: $0"
  echo "  Local:     $0 /ruta/a/datos"
  exit 1
fi

if [ ! -f "$PROFILES_FILE" ]; then
  echo "⚠️  Advertencia: Archivo $PROFILES_FILE no encontrado"
  echo "   Se omitirán los emails"
  PROFILES_FILE=""
fi

echo "📂 Archivos:"
echo "   Voluntarios: $VOLUNTEERS_FILE"
echo "   Perfiles:    ${PROFILES_FILE:-N/A}"
echo ""

# Crear archivo temporal con voluntarios del evento
jq -r --arg eventId "$EVENT_ID" '
  to_entries[] | 
  select(.value.event_id == $eventId) | 
  .value
' "$VOLUNTEERS_FILE" > /tmp/volunteers_temp.json

VOLUNTEER_COUNT=$(cat /tmp/volunteers_temp.json | jq -s 'length')
echo "✅ Voluntarios encontrados: $VOLUNTEER_COUNT"
echo ""

# Crear CSV con headers
echo "Nombre,Email,User ID,Estado,Sobre mí,Razón para unirse,Fecha creación" > "$OUTPUT_FILE"

# Si hay archivo de perfiles, hacer join
if [ -n "$PROFILES_FILE" ]; then
  jq -r --slurpfile profiles "$PROFILES_FILE" '
    . as $volunteer |
    ($profiles[0] | to_entries | map(select(.value.userId == $volunteer.applicant_user_id)) | .[0].value // {}) as $profile |
    [
      $volunteer.applicant_name // "N/A",
      $profile.email // "N/A",
      $volunteer.applicant_user_id,
      $volunteer.status,
      ($volunteer.about_me // "" | gsub("\n"; " ") | gsub("\""; "\"\"") ),
      ($volunteer.join_reason // "" | gsub("\n"; " ") | gsub("\""; "\"\"") ),
      $volunteer.created_at
    ] | 
    @csv
  ' /tmp/volunteers_temp.json >> "$OUTPUT_FILE"
else
  # Sin perfiles, solo datos básicos
  jq -r '
    [
      .applicant_name // "N/A",
      "N/A",
      .applicant_user_id,
      .status,
      (.about_me // "" | gsub("\n"; " ") | gsub("\""; "\"\"") ),
      (.join_reason // "" | gsub("\n"; " ") | gsub("\""; "\"\"") ),
      .created_at
    ] | 
    @csv
  ' /tmp/volunteers_temp.json >> "$OUTPUT_FILE"
fi

# Limpiar archivo temporal
rm -f /tmp/volunteers_temp.json

echo "=================================================="
echo "✅ EXPORTACIÓN COMPLETADA"
echo "=================================================="
echo ""
echo "📄 Archivo generado: $OUTPUT_FILE"
echo "📊 Total de voluntarios: $VOLUNTEER_COUNT"
echo ""
echo "Para ver el contenido:"
echo "  cat $OUTPUT_FILE"
echo ""
echo "Para descargar desde el VPS:"
echo "  scp usuario@vps:$(pwd)/$OUTPUT_FILE ."
echo ""

# Mostrar preview de los primeros 5 voluntarios
echo "📋 Preview (primeras 5 líneas):"
echo "---"
head -6 "$OUTPUT_FILE" | column -t -s','
echo "---"
