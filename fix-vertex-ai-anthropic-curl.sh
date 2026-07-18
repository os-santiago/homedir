#!/bin/bash
# Habilitar data sharing usando la REST API de Vertex AI

PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-your-project-id}"
REGION="${VERTEX_AI_REGION:-us-central1}"

# Obtener el access token
ACCESS_TOKEN=$(gcloud auth print-access-token)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: No se pudo obtener el access token"
    echo "Ejecuta: gcloud auth login"
    exit 1
fi

echo "Habilitando data sharing para Anthropic..."

# Llamar a la API REST
curl -X POST \
  "https://${REGION}-aiplatform.googleapis.com/v1/projects/${PROJECT_ID}/locations/${REGION}/publishers/anthropic:setPublisherModelConfig" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "publisherModelConfig": {
      "dataSharingEnabledProvider": "anthropic"
    }
  }'

echo ""
echo ""
echo "✓ Solicitud enviada"
echo ""
echo "Verifica el estado con:"
echo "curl -H \"Authorization: Bearer \$(gcloud auth print-access-token)\" \\"
echo "  \"https://${REGION}-aiplatform.googleapis.com/v1/projects/${PROJECT_ID}/locations/${REGION}/publishers/anthropic/publisherModelConfig\""
