#!/bin/bash
# Script para habilitar data sharing de Anthropic en Google Cloud Vertex AI

# Variables - Ajusta estos valores
PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-your-project-id}"
REGION="${VERTEX_AI_REGION:-us-central1}"

echo "Configurando data sharing para Anthropic en Vertex AI..."
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo ""

# Verificar que gcloud esté instalado
if ! command -v gcloud &> /dev/null; then
    echo "ERROR: gcloud CLI no está instalado"
    echo "Instala desde: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Verificar autenticación
echo "Verificando autenticación..."
gcloud auth list

# Configurar el proyecto
echo ""
echo "Configurando proyecto..."
gcloud config set project "$PROJECT_ID"

# Habilitar la API de Vertex AI si no está habilitada
echo ""
echo "Habilitando Vertex AI API..."
gcloud services enable aiplatform.googleapis.com --project="$PROJECT_ID"

# Habilitar data sharing para Anthropic
echo ""
echo "Habilitando data sharing para Anthropic..."
gcloud ai models update-publisher-model-config \
    --publisher=anthropic \
    --data-sharing-enabled \
    --project="$PROJECT_ID" \
    --region="$REGION"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Data sharing habilitado exitosamente para Anthropic"
    echo ""
    echo "Puedes verificar la configuración con:"
    echo "gcloud ai models get-publisher-model-config --publisher=anthropic --project=$PROJECT_ID --region=$REGION"
else
    echo ""
    echo "✗ Error al habilitar data sharing"
    echo ""
    echo "Intenta manualmente con:"
    echo "gcloud ai models update-publisher-model-config --publisher=anthropic --data-sharing-enabled --project=$PROJECT_ID --region=$REGION"
fi
