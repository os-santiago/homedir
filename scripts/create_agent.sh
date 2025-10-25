#!/usr/bin/env bash
set -euo pipefail

: "${ELEVENLABS_API_KEY:?Missing ELEVENLABS_API_KEY}"
: "${ELEVENLABS_BASE_URL:=https://api.elevenlabs.io}"

mkdir -p ./.navia

curl -s -X POST "${ELEVENLABS_BASE_URL}/v1/agents" \
  -H "xi-api-key: ${ELEVENLABS_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Navia MVP Agent",
    "description": "Agente para responder preguntas sobre el contenido del sitio EventFlow cacheado",
    "text_only": true,
    "voice_enabled": false,
    "rag_enabled": true
  }' | tee ./.navia/agent.json
