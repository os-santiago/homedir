import glob
import json
import os
import time
from pathlib import Path

import requests

API = os.environ.get("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
KEY = os.environ.get("ELEVENLABS_API_KEY")
if not KEY:
    raise SystemExit(
        "No se encontró la variable de entorno ELEVENLABS_API_KEY. "
        "Configúrala con tu API key de ElevenLabs antes de ejecutar este script."
    )
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


def _normalise_metadata(meta: dict) -> dict:
    """Return a metadata mapping compatible with the ElevenLabs API."""

    if not isinstance(meta, dict):
        return {}

    normalised = {}
    for key, value in meta.items():
        if value is None:
            continue
        if isinstance(value, (str, int, float, bool)):
            normalised[key] = value
        else:
            try:
                normalised[key] = json.dumps(value, ensure_ascii=False)
            except (TypeError, ValueError):
                normalised[key] = str(value)
    return normalised


def create_doc(agent_id: str, meta: dict, text: str, index: int) -> dict:
    url = f"{API}/v1/convai/knowledge-base"
    params = {"agent_id": agent_id}
    data = {"name": meta.get("title_guess", f"Navia Chunk {index}")}
    metadata = _normalise_metadata(meta)
    if metadata:
        data["metadata"] = json.dumps(metadata, ensure_ascii=False)
    filename = f"chunk-{index}.txt"
    files = {"file": (filename, text.encode("utf-8"), "text/plain; charset=utf-8")}
    response = requests.post(url, headers=AUTH_HEADERS, params=params, data=data, files=files, timeout=60)
    try:
        response.raise_for_status()
    except requests.HTTPError as exc:
        detail = ""
        try:
            payload = response.json()
            detail = payload.get("detail") or payload.get("message") or payload
        except ValueError:
            detail = response.text
        raise SystemExit(f"Error al subir el chunk {index}: {response.status_code} → {detail}") from exc
    return response.json()


def compute_rag(doc_id: str) -> dict:
    url = f"{API}/v1/convai/knowledge-base/{doc_id}/rag-index"
    payload = {"model": "multilingual_e5_large_instruct"}
    response = requests.post(url, headers=JSON_HEADERS, json=payload, timeout=60)
    response.raise_for_status()
    return response.json()


def main() -> None:
    files = glob.glob("./.navia/chunks/*.json")
    if not files:
        raise SystemExit("No se encontraron chunks en ./.navia/chunks. Ejecuta scripts/normalize_and_chunk.py primero.")

    if DOC_MAP_PATH.exists():
        try:
            doc_map = json.loads(DOC_MAP_PATH.read_text())
        except json.JSONDecodeError:
            doc_map = {}
    else:
        doc_map = {}

    for index, file_path in enumerate(files, start=1):
        data = json.loads(Path(file_path).read_text())
        doc = create_doc(AGENT, data["meta"], data["text"], index)
        doc_id = doc.get("id") or doc.get("document_id")
        if doc_id:
            metadata = dict(data.get("meta", {}))
            metadata.setdefault("doc_id", doc_id)
            if "source" in metadata and "source_url" not in metadata:
                metadata["source_url"] = metadata["source"]
            doc_map[doc_id] = metadata
            compute_rag(doc_id)
        if index % 25 == 0:
            time.sleep(1)

    DOC_MAP_PATH.write_text(json.dumps(doc_map, ensure_ascii=False, indent=2))
    print(f"Subidos {len(files)} chunks al agente {AGENT}")


if __name__ == "__main__":
    main()
