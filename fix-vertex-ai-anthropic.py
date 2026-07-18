#!/usr/bin/env python3
"""
Script para habilitar data sharing de Anthropic en Google Cloud Vertex AI
Requiere: pip install google-cloud-aiplatform
"""

import os
from google.cloud import aiplatform

# Configuración
PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "your-project-id")
REGION = os.environ.get("VERTEX_AI_REGION", "us-central1")

def enable_anthropic_data_sharing():
    """Habilita el data sharing para el publisher Anthropic en Vertex AI"""

    print(f"Configurando data sharing para Anthropic en Vertex AI...")
    print(f"Project: {PROJECT_ID}")
    print(f"Region: {REGION}")
    print()

    # Inicializar Vertex AI
    aiplatform.init(project=PROJECT_ID, location=REGION)

    try:
        # Habilitar data sharing para Anthropic
        # Nota: La API exacta puede variar según la versión del SDK
        # Puedes necesitar usar directamente la API REST

        from google.cloud.aiplatform_v1 import ModelServiceClient
        from google.cloud.aiplatform_v1.types import PublisherModelConfig

        client = ModelServiceClient()
        parent = f"projects/{PROJECT_ID}/locations/{REGION}"

        # Configurar el publisher model config
        config = PublisherModelConfig(
            data_sharing_enabled_provider="anthropic"
        )

        # Actualizar la configuración
        request = {
            "parent": parent,
            "publisher": "anthropic",
            "publisher_model_config": config
        }

        response = client.set_publisher_model_config(**request)

        print("✓ Data sharing habilitado exitosamente para Anthropic")
        print(f"Response: {response}")

    except Exception as e:
        print(f"✗ Error al habilitar data sharing: {e}")
        print()
        print("Alternativa: Usa el comando gcloud:")
        print(f"gcloud ai models update-publisher-model-config --publisher=anthropic --data-sharing-enabled --project={PROJECT_ID} --region={REGION}")

if __name__ == "__main__":
    enable_anthropic_data_sharing()
