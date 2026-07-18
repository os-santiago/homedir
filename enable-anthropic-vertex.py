#!/usr/bin/env python3
"""
Script para verificar el acceso a modelos de Anthropic en Vertex AI
y proporcionar instrucciones si no está habilitado.
"""

import os
import sys

try:
    from google.cloud import aiplatform
    from anthropic import AnthropicVertex
except ImportError:
    print("ERROR: Faltan dependencias. Instala con:")
    print("pip install google-cloud-aiplatform anthropic[vertex]")
    sys.exit(1)

PROJECT_ID = "na-services-ai-tooling"
REGION = "us-central1"

def check_anthropic_access():
    """Verifica si se puede acceder a los modelos de Anthropic"""

    print(f"Verificando acceso a Anthropic en Vertex AI...")
    print(f"Project: {PROJECT_ID}")
    print(f"Region: {REGION}")
    print()

    try:
        # Intentar crear un cliente de Anthropic Vertex
        client = AnthropicVertex(
            project_id=PROJECT_ID,
            region=REGION
        )

        # Intentar hacer una llamada simple
        message = client.messages.create(
            model="claude-3-5-sonnet-v2@20241022",
            max_tokens=10,
            messages=[{"role": "user", "content": "Hi"}]
        )

        print("✓ Acceso a Anthropic habilitado correctamente!")
        print(f"Response: {message.content[0].text}")
        return True

    except Exception as e:
        error_msg = str(e)

        if "403" in error_msg and "data_sharing" in error_msg.lower():
            print("✗ Data sharing NO está habilitado para Anthropic")
            print()
            print("SOLUCIÓN:")
            print("1. Ve a la consola de Google Cloud:")
            print(f"   https://console.cloud.google.com/vertex-ai/publishers/anthropic?project={PROJECT_ID}")
            print()
            print("2. O busca 'Claude' en Model Garden:")
            print(f"   https://console.cloud.google.com/vertex-ai/model-garden?project={PROJECT_ID}")
            print()
            print("3. Acepta los términos de servicio de Anthropic y habilita data sharing")
            print()
            print("4. Vuelve a ejecutar este script para verificar")

        else:
            print(f"✗ Error al acceder a Anthropic: {error_msg}")

        return False

if __name__ == "__main__":
    check_anthropic_access()
