#!/bin/bash
set -euo pipefail
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

# Archivos temporales únicos para evitar carreras y ataques de symlink.
VOLUNTEERS_TMP="$(mktemp "${OUTPUT_FILE}.volunteers.XXXXXX")"
OUTPUT_TMP="$(mktemp "${OUTPUT_FILE}.XXXXXX")"
trap 'rm -f "$VOLUNTEERS_TMP" "$OUTPUT_TMP"' EXIT

echo "📂 Archivos:"
echo "   Voluntarios: $VOLUNTEERS_FILE"
echo "   Perfiles:    ${PROFILES_FILE:-N/A}"
echo ""

# Extraer voluntarios del evento a un stream temporal.
jq -r --arg eventId "$EVENT_ID" '
  to_entries[] |
  select(.value.event_id == $eventId) |
  .value
' "$VOLUNTEERS_FILE" > "$VOLUNTEERS_TMP"

# Contar registros reales y fallar cerrado si no hay ninguno.
VOLUNTEER_COUNT=$(jq -s 'length' "$VOLUNTEERS_TMP")
if [ "$VOLUNTEER_COUNT" -eq 0 ]; then
  echo "❌ Error: No se encontraron voluntarios para el evento $EVENT_ID"
  exit 1
fi
echo "✅ Voluntarios encontrados: $VOLUNTEER_COUNT"
echo ""

# Encabezado del CSV
CSV_HEADER='Nombre,Email,User ID,Estado,Sobre mí,Razón para unirse,Fecha creación'

# Apóstrofo (') para neutralizar prefijos de fórmula de hoja de cálculo.
QUOTE="'"

# Neutralización de prefijos de fórmula y escape de comillas/saltos de línea.
# @csv ya escapa comillas; solo se antepone el apóstrofo a valores peligrosos.
if [ -n "$PROFILES_FILE" ]; then
  # Unir con perfiles por applicant_user_id.
  jq -r --arg quote "$QUOTE" --slurpfile profiles "$PROFILES_FILE" '
    def sanitize:
      if ((length > 0) and ((.[0:1] == "=") or (.[0:1] == "+") or (.[0:1] == "-") or (.[0:1] == "@"))) then ($quote + .) else . end;
    . as $volunteer |
    (($profiles[0] | to_entries[] | select(.value.userId == $volunteer.applicant_user_id) | .value) // {}) as $profile |
    [
      ($volunteer.applicant_name // "N/A" | sanitize),
      ($profile.email // "N/A" | sanitize),
      ($volunteer.applicant_user_id // "" | sanitize),
      ($volunteer.status // "" | sanitize),
      ($volunteer.about_me // "" | sanitize),
      ($volunteer.join_reason // "" | sanitize),
      ($volunteer.created_at // "" | sanitize)
    ] | @csv
  ' "$VOLUNTEERS_TMP" > "$OUTPUT_TMP"
else
  # Sin perfiles, solo datos básicos
  jq -r --arg quote "$QUOTE" '
    def sanitize:
      if ((length > 0) and ((.[0:1] == "=") or (.[0:1] == "+") or (.[0:1] == "-") or (.[0:1] == "@"))) then ($quote + .) else . end;
    [
      (.applicant_name // "N/A" | sanitize),
      "N/A",
      (.applicant_user_id // "" | sanitize),
      (.status // "" | sanitize),
      (.about_me // "" | sanitize),
      (.join_reason // "" | sanitize),
      (.created_at // "" | sanitize)
    ] | @csv
  ' "$VOLUNTEERS_TMP" > "$OUTPUT_TMP"
fi

# Anteponer encabezado y publicar de forma atómica.
{
  echo "$CSV_HEADER"
  cat "$OUTPUT_TMP"
} > "${OUTPUT_TMP}.final"
mv "${OUTPUT_TMP}.final" "$OUTPUT_FILE"
trap - EXIT

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