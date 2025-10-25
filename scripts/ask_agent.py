import json
import os
import time
from pathlib import Path
from typing import Dict, Iterable, List

import requests

API = os.environ.get("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
KEY = os.environ["ELEVENLABS_API_KEY"]
AGENT_PATH = Path("./.navia/agent.json")

if not AGENT_PATH.exists():
    raise SystemExit("No se encontró ./.navia/agent.json. Ejecuta scripts/create_agent.sh primero.")

AGENT = json.loads(AGENT_PATH.read_text())["id"]
HEADERS = {"xi-api-key": KEY, "Content-Type": "application/json"}


def map_documents(agent_id: str) -> Dict[str, Dict]:
    """Return a mapping doc_id -> metadata by walking the knowledge base."""
    url = f"{API}/v1/agents/{agent_id}/knowledge-base/documents"
    response = requests.get(url, headers={"xi-api-key": KEY}, timeout=60)
    response.raise_for_status()
    payload = response.json()
    if isinstance(payload, dict):
        docs = payload.get("documents", [])
    else:
        docs = payload
    mapping = {}
    for doc in docs:
        mapping[doc["id"]] = doc.get("metadata", {})
    return mapping


def poll_conversation(conv_id: str, delay: float = 1.0, attempts: int = 10) -> dict:
    url = f"{API}/v1/agents/{AGENT}/conversations/{conv_id}"
    for _ in range(attempts):
        response = requests.get(url, headers=HEADERS, timeout=60)
        response.raise_for_status()
        data = response.json()
        messages: List[dict] = data.get("messages", [])
        if messages and messages[-1].get("role") == "assistant":
            return data
        time.sleep(delay)
    return data


def extract_references(payload: dict) -> Iterable[Dict]:
    refs = payload.get("rag_references") or []
    if refs:
        return refs
    # Fallback: ElevenLabs may expose doc IDs in conversation metadata
    last_message = payload.get("messages", [{}])[-1]
    return last_message.get("documents", [])


def main() -> None:
    question = input("Pregunta (ej: ¿Dónde está el evento 'Test event'?): ")

    conv_response = requests.post(
        f"{API}/v1/agents/{AGENT}/conversations", headers=HEADERS, json={"mode": "text"}, timeout=60
    )
    conv_response.raise_for_status()
    conversation = conv_response.json()
    conv_id = conversation["id"]

    requests.post(
        f"{API}/v1/agents/{AGENT}/conversations/{conv_id}/messages",
        headers=HEADERS,
        json={"role": "user", "content": question},
        timeout=60,
    ).raise_for_status()

    data = poll_conversation(conv_id)

    answer = data.get("messages", [{}])[-1].get("content", "(sin respuesta)")
    print("\n=== RESPUESTA DEL AGENTE ===\n", answer)

    references = list(extract_references(data))
    if not references:
        print("\nNo se recibieron referencias directas. Intentando mapear doc_ids → metadata...")
        docs = map_documents(AGENT)
        doc_ids = set()
        for ref in data.get("messages", []):
            for document in ref.get("documents", []) or []:
                if isinstance(document, dict):
                    doc_ids.add(document.get("id") or document.get("doc_id"))
                else:
                    doc_ids.add(document)
        references = [
            {"document": {"metadata": docs.get(doc_id, {"doc_id": doc_id})}}
            for doc_id in doc_ids
            if doc_id
        ]

    if references:
        print("\nFuentes:")
        for ref in references:
            metadata = (ref.get("document") or {}).get("metadata", {})
            print("-", metadata.get("source_url") or metadata.get("doc_id", "(sin url)"))
    else:
        print("\n(Sin referencias disponibles. Revisa la configuración del agente RAG.)")


if __name__ == "__main__":
    main()
