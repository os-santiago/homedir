import json
import os
import time
from pathlib import Path
from typing import Dict, Iterable, List, Sequence

import requests

API = os.environ.get("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
KEY = os.environ["ELEVENLABS_API_KEY"]
AGENT_PATH = Path("./.navia/agent.json")
DOC_MAP_PATH = Path("./.navia/documents.json")

if not AGENT_PATH.exists():
    raise SystemExit("No se encontró ./.navia/agent.json. Ejecuta scripts/create_agent.sh primero.")

try:
    agent_payload = json.loads(AGENT_PATH.read_text())
except json.JSONDecodeError as exc:
    raise SystemExit("No se pudo leer ./.navia/agent.json. Vuelve a crear el agente.") from exc

AGENT = agent_payload.get("id") or agent_payload.get("agent_id")
if not AGENT:
    raise SystemExit("El archivo ./.navia/agent.json no contiene un agent_id válido.")

AUTH_HEADERS = {"xi-api-key": KEY}
JSON_HEADERS = {**AUTH_HEADERS, "Content-Type": "application/json"}


def load_local_doc_map() -> Dict[str, Dict]:
    if not DOC_MAP_PATH.exists():
        return {}
    try:
        return json.loads(DOC_MAP_PATH.read_text())
    except json.JSONDecodeError:
        return {}


def fetch_remote_metadata(agent_id: str, doc_id: str) -> Dict:
    url = f"{API}/v1/convai/knowledge-base/{doc_id}"
    response = requests.get(url, headers=AUTH_HEADERS, params={"agent_id": agent_id}, timeout=60)
    if not response.ok:
        return {}
    payload = response.json()
    if isinstance(payload, dict):
        if "metadata" in payload and isinstance(payload["metadata"], dict):
            return payload["metadata"]
        document = payload.get("document")
        if isinstance(document, dict) and isinstance(document.get("metadata"), dict):
            return document["metadata"]
    return {}


def resolve_doc_metadata(agent_id: str, doc_ids: Sequence[str]) -> Dict[str, Dict]:
    mapping: Dict[str, Dict] = {}
    local_map = load_local_doc_map()
    for doc_id in doc_ids:
        if not doc_id or doc_id in mapping:
            continue
        metadata = local_map.get(doc_id)
        if not metadata:
            metadata = fetch_remote_metadata(agent_id, doc_id)
        if metadata:
            mapping[doc_id] = metadata
    return mapping


def extract_messages(payload: dict) -> List[dict]:
    if not isinstance(payload, dict):
        return []
    messages = payload.get("messages")
    if isinstance(messages, list):
        return messages
    conversation = payload.get("conversation")
    if isinstance(conversation, dict):
        conv_messages = conversation.get("messages")
        if isinstance(conv_messages, list):
            return conv_messages
    return []


def request_with_fallback(
    method: str,
    agent_path: str,
    *,
    fallback_path: str | None = None,
    json_payload: Dict | None = None,
    params: Dict | None = None,
    timeout: int = 60,
):
    """Send a request trying the legacy agent scoped path first and fall back otherwise."""

    def clone_payload(payload: Dict | None) -> Dict | None:
        if payload is None:
            return None
        return dict(payload)

    method_upper = method.upper()
    uses_body = method_upper in {"POST", "PUT", "PATCH"}

    url = f"{API}{agent_path}".format(agent_id=AGENT)
    response = requests.request(
        method,
        url,
        headers=JSON_HEADERS if uses_body else AUTH_HEADERS,
        json=clone_payload(json_payload),
        params=clone_payload(params),
        timeout=timeout,
    )

    if response.status_code == 404 and fallback_path:
        fallback_json = clone_payload(json_payload)
        fallback_params = clone_payload(params)

        if uses_body:
            fallback_json = fallback_json or {}
            fallback_json.setdefault("agent_id", AGENT)
        else:
            fallback_params = fallback_params or {}
            fallback_params.setdefault("agent_id", AGENT)

        fallback_url = f"{API}{fallback_path}".format(agent_id=AGENT)
        response = requests.request(
            method,
            fallback_url,
            headers=JSON_HEADERS if uses_body else AUTH_HEADERS,
            json=fallback_json if fallback_json else None,
            params=fallback_params if fallback_params else None,
            timeout=timeout,
        )

    response.raise_for_status()
    return response


def poll_conversation(conv_id: str, delay: float = 1.0, attempts: int = 10) -> dict:
    for _ in range(attempts):
        response = request_with_fallback(
            "GET",
            f"/v1/convai/agents/{{agent_id}}/conversations/{conv_id}",
            fallback_path=f"/v1/convai/conversations/{conv_id}",
        )
        data = response.json()
        messages = extract_messages(data)
        if messages and messages[-1].get("role") == "assistant":
            return data
        time.sleep(delay)
    return data


def extract_references(payload: dict) -> Iterable[Dict]:
    if not isinstance(payload, dict):
        return []
    refs = payload.get("rag_references") or []
    if refs:
        return refs
    last_message = {}
    messages = extract_messages(payload)
    if messages:
        last_message = messages[-1]
    return last_message.get("documents", []) or []


def main() -> None:
    question = input("Pregunta (ej: ¿Dónde está el evento 'Test event'?): ")

    conv_response = request_with_fallback(
        "POST",
        "/v1/convai/agents/{agent_id}/conversations",
        fallback_path="/v1/convai/conversations",
        json_payload={"mode": "text", "agent_id": AGENT},
    )
    conversation = conv_response.json()
    conv_id = (
        conversation.get("id")
        or conversation.get("conversation_id")
        or (conversation.get("conversation", {}) if isinstance(conversation, dict) else {}).get("id")
    )
    if not conv_id:
        raise SystemExit("No se pudo crear la conversación. Revisa la respuesta de la API.")

    request_with_fallback(
        "POST",
        f"/v1/convai/agents/{{agent_id}}/conversations/{conv_id}/messages",
        fallback_path=f"/v1/convai/conversations/{conv_id}/messages",
        json_payload={"role": "user", "content": question, "agent_id": AGENT},
    )

    data = poll_conversation(conv_id)

    messages = extract_messages(data)
    answer = "(sin respuesta)"
    if messages:
        answer = messages[-1].get("content") or messages[-1].get("text", "(sin respuesta)")
    print("\n=== RESPUESTA DEL AGENTE ===\n", answer)

    references = list(extract_references(data))
    doc_ids: List[str] = []
    if not references:
        print("\nNo se recibieron referencias directas. Intentando mapear doc_ids → metadata...")
        for message in messages:
            for document in message.get("documents", []) or []:
                if isinstance(document, dict):
                    doc_id = document.get("id") or document.get("doc_id")
                else:
                    doc_id = document
                if doc_id:
                    doc_ids.append(doc_id)
        references = []
    else:
        for ref in references:
            document = ref.get("document") or {}
            if isinstance(document, dict):
                doc_ids.append(document.get("id") or document.get("doc_id") or (document.get("metadata") or {}).get("doc_id"))
            else:
                doc_ids.append(ref.get("id") or ref.get("doc_id"))

    metadata_map = resolve_doc_metadata(AGENT, [doc_id for doc_id in doc_ids if doc_id])

    if references and not metadata_map:
        # Try to populate metadata_map from reference objects directly
        for ref in references:
            document = ref.get("document") or {}
            if isinstance(document, dict):
                doc_meta = document.get("metadata")
                doc_id = document.get("id") or document.get("doc_id") or (doc_meta or {}).get("doc_id")
                if doc_id and isinstance(doc_meta, dict):
                    metadata_map.setdefault(doc_id, doc_meta)

    if metadata_map:
        print("\nFuentes:")
        for doc_id, metadata in metadata_map.items():
            source = metadata.get("source_url") or metadata.get("doc_id") or metadata.get("name")
            print("-", source or doc_id)
    elif doc_ids:
        print("\nFuentes:")
        for doc_id in dict.fromkeys(doc_ids):
            print("-", doc_id)
    else:
        print("\n(Sin referencias disponibles. Revisa la configuración del agente RAG.)")


if __name__ == "__main__":
    main()
