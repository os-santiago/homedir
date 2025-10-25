#!/usr/bin/env bash
set -euo pipefail

: "${ELEVENLABS_API_KEY:?Missing ELEVENLABS_API_KEY}"
: "${ELEVENLABS_BASE_URL:=https://api.elevenlabs.io}"

mkdir -p ./.navia

response_tmp=$(mktemp)
payload_tmp=$(mktemp)

cleanup() {
  rm -f "${response_tmp}" "${payload_tmp}"
}

trap cleanup EXIT

cat >"${payload_tmp}" <<'JSON'
{
  "conversation_config": {
    "agent": {
      "first_message": "Hola, soy Navia. Buscaré en mis documentos indexados y te responderé solo con información verificada indicando la URL exacta de donde proviene.",
      "language": "es",
      "prompt": {
        "prompt": "Eres Navia, un asistente experto en el contenido cacheado del sitio EventFlow. Tu tarea es buscar el contenido solicitado por la persona usuaria dentro de los chunks recuperados por RAG, verificar que el texto del chunk (campo content) coincide con lo pedido y responder en español citando la URL del metadata (source_url) desde la que proviene la evidencia. Usa exclusivamente la información recuperada mediante RAG desde tu base de conocimiento y, si no existe evidencia suficiente o el chunk no coincide, responde de forma explícita que no cuentas con datos para responder.",
        "llm": "gpt-4o-mini",
        "rag": {
          "enabled": true,
          "embedding_model": "multilingual_e5_large_instruct"
        }
      }
    },
    "conversation": {
      "text_only": true
    }
  },
  "name": "Navia MVP Agent",
  "tags": ["navia", "eventflow", "mvp"]
}
JSON

curl --fail-with-body -sS -X POST "${ELEVENLABS_BASE_URL}/v1/convai/agents/create" \
  -H "xi-api-key: ${ELEVENLABS_API_KEY}" \
  -H "Content-Type: application/json" \
  --data-binary "@${payload_tmp}" \
  | tee "${response_tmp}"

python - "${response_tmp}" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as fp:
    data = json.load(fp)

agent_id = data.get("agent_id") or data.get("id")
if not agent_id:
    raise SystemExit("La respuesta no incluye un agent_id. Revisa ./.navia/agent.json para más detalles.")

print(f"Agente creado con id: {agent_id}")
PY

mv "${response_tmp}" ./.navia/agent.json
trap - EXIT
